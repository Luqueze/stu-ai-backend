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
            - Varie o tamanho dos enunciados ao longo da prova, misturando três formatos:
              * curtos e diretos (3 a 4 frases), com uma pergunta objetiva que ainda exija raciocínio;
              * médios (5 a 6 frases), com um contexto ou situação-problema breve;
              * longos (7 a 9 frases), com caso prático, trecho de código, dados ou cenário realista detalhado.
            - Nenhum formato deve ultrapassar metade das questões; alterne-os sem seguir um padrão previsível.
            - Termine com um comando claro e inequívoco (por exemplo: "Com base nessa situação, qual...").
            - Evite perguntas triviais do tipo "O que é X?" ou que possam ser respondidas só pela memorização de um termo.
            - Varie os subtópicos do tema e os tipos de habilidade cobrados (interpretação, análise, aplicação, \
            comparação, identificação de erros), sem repetir o mesmo conceito entre questões.

            Requisitos para as alternativas (campo options):
            - Exatamente 4 alternativas por questão, sem letras ou numeração no início do texto.
            - Exatamente UMA alternativa correta. As outras três devem estar inequivocamente erradas para \
            um especialista no tema: não podem ser parcialmente corretas, corretas sob outra interpretação \
            do enunciado nem depender de suposições não informadas.
            - As alternativas devem ser genuinamente diferentes entre si, cada uma expressando uma ideia, \
            conceito, valor ou raciocínio distinto. É proibido criar alternativas quase idênticas que diferem \
            apenas por uma palavra, um número, uma negação ou o trecho final da frase.
            - Cada alternativa incorreta deve representar um erro diferente (conceito confundido, aplicação \
            indevida, interpretação equivocada do cenário, caso de borda ignorado), plausível e não obviamente absurda.
            - Dentro de uma mesma questão, mantenha extensão e nível de detalhe comparáveis entre as alternativas, \
            para que a correta não se destaque por ser a mais longa ou a mais específica.
            - Não use "todas as anteriores", "nenhuma das anteriores" ou alternativas que se sobreponham ou se \
            impliquem mutuamente.
            - Distribua a posição da alternativa correta de forma variada entre as questões.

            O campo correctOptionIndex é um índice de base zero na lista de alternativas: \
            0 para a 1ª alternativa, 1 para a 2ª, 2 para a 3ª e 3 para a 4ª. Nunca use o valor 4.

            Antes de responder, revise cada questão:
            1. Confirme que a alternativa indicada em correctOptionIndex é correta e que nenhuma outra também é.
            2. Compare as alternativas entre si; se duas forem parecidas ou diferirem só em um detalhe, reescreva uma delas.
            3. Verifique que os enunciados têm tamanhos variados ao longo da prova.

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
