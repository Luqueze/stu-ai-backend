package com.aiexam.aigeneratorservice.service;

import com.aiexam.aigeneratorservice.config.RabbitMQConfig;
import com.aiexam.aigeneratorservice.config.StructuredOutputOptionsFactory;
import com.aiexam.aigeneratorservice.dto.GeneratedExamPayload;
import com.aiexam.aigeneratorservice.exception.InvalidGeneratedContentException;
import com.aiexam.commonevents.ExamGenerationCompletedEvent;
import com.aiexam.commonevents.ExamGenerationFailedEvent;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import com.aiexam.commonevents.FailureReason;
import com.aiexam.commonevents.GeneratedQuestion;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExamQuestionGenerationService {

    private static final String PROMPT_TEMPLATE =
            """
            Gere exatamente {questionCount} questões de múltipla escolha sobre o tema "{theme}", \
            no nível de dificuldade {difficulty}.
            Cada questão deve ter exatamente 4 alternativas, com apenas uma correta.
            Responda estritamente no formato JSON definido pelo schema fornecido.
            """;

    private static final Pattern STATUS_CODE_PATTERN = Pattern.compile("\\b(\\d{3})\\b");

    private final ChatClient chatClient;
    private final StructuredOutputOptionsFactory structuredOutputOptionsFactory;
    private final RabbitTemplate rabbitTemplate;
    private final BeanOutputConverter<GeneratedExamPayload> outputConverter =
            new BeanOutputConverter<>(GeneratedExamPayload.class);

    public void generate(ExamGenerationRequestedEvent event) {
        GeneratedExamPayload payload;
        try {
            payload = callModel(event);
            validate(payload, event.questionCount());
        } catch (NonTransientAiException ex) {
            log.warn("Non-transient AI error generating exam {}", event.examId(), ex);
            publishFailure(event.examId(), classifyNonTransient(ex), ex.getMessage());
            return;
        } catch (TransientAiException ex) {
            log.warn("Transient AI error generating exam {} after internal retries", event.examId(), ex);
            publishFailure(event.examId(), FailureReason.TIMEOUT, ex.getMessage());
            return;
        } catch (InvalidGeneratedContentException ex) {
            log.warn("Invalid generated content for exam {}", event.examId(), ex);
            publishFailure(event.examId(), FailureReason.INVALID_RESPONSE, ex.getMessage());
            return;
        }

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_COMPLETED,
                new ExamGenerationCompletedEvent(event.examId(), payload.questions()));
    }

    private GeneratedExamPayload callModel(ExamGenerationRequestedEvent event) {
        ChatOptions options =
                structuredOutputOptionsFactory.create(outputConverter.getJsonSchema(), outputConverter.getJsonSchemaMap());

        String content;
        try {
            content =
                    chatClient
                            .prompt()
                            .user(
                                    u ->
                                            u.text(PROMPT_TEMPLATE)
                                                    .param("questionCount", event.questionCount())
                                                    .param("theme", event.theme())
                                                    .param("difficulty", event.difficulty()))
                            .options(options)
                            .call()
                            .content();
        } catch (NonTransientAiException | TransientAiException ex) {
            // OpenAI already classifies its own errors this way; rethrow unchanged.
            throw ex;
        } catch (ResourceAccessException ex) {
            // e.g. Ollama unreachable (connection refused) - no Spring AI classification exists for this provider.
            throw new TransientAiException("Connection error calling AI provider: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            // Ollama's own error handler throws a plain RuntimeException for HTTP error statuses.
            throw new NonTransientAiException(ex.getMessage(), ex);
        }

        try {
            return outputConverter.convert(content);
        } catch (RuntimeException ex) {
            throw new InvalidGeneratedContentException("Failed to parse AI response as JSON: " + ex.getMessage());
        }
    }

    private void validate(GeneratedExamPayload payload, int expectedQuestionCount) {
        if (payload == null || payload.questions() == null || payload.questions().size() != expectedQuestionCount) {
            throw new InvalidGeneratedContentException(
                    "Expected %d questions but got %d"
                            .formatted(
                                    expectedQuestionCount,
                                    payload == null || payload.questions() == null ? 0 : payload.questions().size()));
        }
        for (GeneratedQuestion question : payload.questions()) {
            if (question.options() == null
                    || question.options().isEmpty()
                    || question.correctOptionIndex() < 0
                    || question.correctOptionIndex() >= question.options().size()) {
                throw new InvalidGeneratedContentException(
                        "Question has an out-of-range correctOptionIndex: " + question);
            }
        }
    }

    private FailureReason classifyNonTransient(NonTransientAiException ex) {
        Matcher matcher = STATUS_CODE_PATTERN.matcher(String.valueOf(ex.getMessage()));
        if (matcher.find() && "429".equals(matcher.group(1))) {
            return FailureReason.QUOTA_EXCEEDED;
        }
        return FailureReason.LLM_ERROR;
    }

    private void publishFailure(UUID examId, FailureReason reason, String message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_FAILED,
                new ExamGenerationFailedEvent(examId, reason, message));
    }
}
