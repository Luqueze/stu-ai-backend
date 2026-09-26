package com.aiexam.aigeneratorservice.service;

import com.aiexam.aigeneratorservice.config.RabbitMQConfig;
import com.aiexam.aigeneratorservice.config.StructuredOutputOptionsFactory;
import com.aiexam.aigeneratorservice.dto.GeneratedExamPayload;
import com.aiexam.aigeneratorservice.dto.GeneratedQuestionPayload;
import com.aiexam.aigeneratorservice.exception.InvalidGeneratedContentException;
import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.commonevents.ExamGenerationCompletedEvent;
import com.aiexam.commonevents.ExamGenerationFailedEvent;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import com.aiexam.commonevents.FailureReason;
import com.aiexam.commonevents.GeneratedQuestion;
import java.util.List;
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

    private static final String SYSTEM_PROMPT =
            """
            Você é um professor especialista e elaborador experiente de provas para avaliações de alto nível \
            (vestibulares, concursos e exames de certificação). Suas questões avaliam compreensão profunda, \
            raciocínio e aplicação de conceitos, e não apenas memorização de definições. \
            Escreva sempre em português do Brasil, com linguagem clara, precisa e tecnicamente correta.
            """;

    private static final String PROMPT_TEMPLATE =
            """
            Elabore exatamente {questionCount} questões de múltipla escolha sobre o tema "{theme}".

            Nível de dificuldade: {difficulty}.
            {difficultyGuidelines}

            Requisitos para o enunciado (campo statement):
            - Cada enunciado deve ser aprofundado, com 3 a 6 frases: apresente um contexto, situação-problema, \
            caso prático, trecho de código, dado ou cenário realista antes de fazer a pergunta.
            - Termine com um comando claro e inequívoco (por exemplo: "Com base nessa situação, qual...").
            - Evite perguntas triviais do tipo "O que é X?" ou que possam ser respondidas só pela memorização de um termo.
            - Varie os subtópicos do tema e os tipos de habilidade cobrados (interpretação, análise, aplicação, \
            comparação, identificação de erros), sem repetir o mesmo conceito entre questões.

            Requisitos para as alternativas (campo options):
            - Exatamente 4 alternativas por questão, com apenas uma correta.
            - Alternativas completas e com extensão semelhante, sem letras ou numeração no início do texto.
            - As alternativas incorretas devem ser plausíveis, baseadas em erros conceituais comuns ou \
            interpretações equivocadas, e não obviamente absurdas.
            - Não use "todas as anteriores", "nenhuma das anteriores" ou alternativas que se sobreponham.
            - Distribua a posição da alternativa correta de forma variada entre as questões.

            O campo correctOptionIndex é um índice de base zero na lista de alternativas: \
            0 para a 1ª alternativa, 1 para a 2ª, 2 para a 3ª e 3 para a 4ª. Nunca use o valor 4.
            Antes de responder, confira que a alternativa indicada é de fato a única correta.
            Responda estritamente no formato JSON definido pelo schema fornecido.
            """;

    private static final Pattern STATUS_CODE_PATTERN = Pattern.compile("\\b(\\d{3})\\b");

    private final ChatClient chatClient;
    private final StructuredOutputOptionsFactory structuredOutputOptionsFactory;
    private final RabbitTemplate rabbitTemplate;
    private final BeanOutputConverter<GeneratedExamPayload> outputConverter =
            new BeanOutputConverter<>(GeneratedExamPayload.class);

    public void generate(ExamGenerationRequestedEvent event) {
        log.info(
                "Calling AI model to generate exam {} (theme='{}', questionCount={}, difficulty={})",
                event.examId(),
                event.theme(),
                event.questionCount(),
                event.difficulty());
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

        List<GeneratedQuestion> questions =
                payload.questions().stream()
                        .map(q -> new GeneratedQuestion(q.statement(), q.options(), q.correctOptionIndex()))
                        .toList();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_COMPLETED,
                new ExamGenerationCompletedEvent(event.examId(), questions));
        log.info("Exam {} generated successfully with {} questions", event.examId(), questions.size());
    }

    private GeneratedExamPayload callModel(ExamGenerationRequestedEvent event) {
        ChatOptions options =
                structuredOutputOptionsFactory.create(outputConverter.getJsonSchema(), outputConverter.getJsonSchemaMap());

        String content;
        try {
            content =
                    chatClient
                            .prompt()
                            .system(SYSTEM_PROMPT)
                            .user(
                                    u ->
                                            u.text(PROMPT_TEMPLATE)
                                                    .param("questionCount", event.questionCount())
                                                    .param("theme", event.theme())
                                                    .param("difficulty", event.difficulty())
                                                    .param("difficultyGuidelines", difficultyGuidelines(event.difficulty())))
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

    private String difficultyGuidelines(DifficultyLevel difficulty) {
        return switch (difficulty) {
            case EASY -> "Cobre conceitos fundamentais do tema, mas sempre aplicados a um contexto concreto, "
                    + "exigindo compreensão e não apenas lembrar uma definição.";
            case MEDIUM -> "Exija aplicação e análise: o aluno deve relacionar dois ou mais conceitos, interpretar "
                    + "o cenário apresentado ou prever o resultado de uma situação.";
            case HARD -> "Exija análise crítica e síntese: cenários complexos com múltiplas etapas de raciocínio, "
                    + "casos de borda, trade-offs ou exceções, com distratores sutis que só um aluno com domínio "
                    + "profundo do tema consiga descartar.";
        };
    }

    private void validate(GeneratedExamPayload payload, int expectedQuestionCount) {
        if (payload == null || payload.questions() == null || payload.questions().size() != expectedQuestionCount) {
            throw new InvalidGeneratedContentException(
                    "Expected %d questions but got %d"
                            .formatted(
                                    expectedQuestionCount,
                                    payload == null || payload.questions() == null ? 0 : payload.questions().size()));
        }
        for (GeneratedQuestionPayload question : payload.questions()) {
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
