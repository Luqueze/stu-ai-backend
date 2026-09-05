package com.aiexam.aigeneratorservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.aigeneratorservice.config.RabbitMQConfig;
import com.aiexam.aigeneratorservice.config.StructuredOutputOptionsFactory;
import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.commonevents.ExamGenerationCompletedEvent;
import com.aiexam.commonevents.ExamGenerationFailedEvent;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import com.aiexam.commonevents.FailureReason;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ExamQuestionGenerationServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private StructuredOutputOptionsFactory structuredOutputOptionsFactory;
    @Mock private RabbitTemplate rabbitTemplate;

    private ExamQuestionGenerationService service;
    private ExamGenerationRequestedEvent event;

    @BeforeEach
    void setUp() {
        service =
                new ExamQuestionGenerationService(
                        chatClient, structuredOutputOptionsFactory, rabbitTemplate);
        event = new ExamGenerationRequestedEvent(UUID.randomUUID(), "Basic Arithmetic", 1, DifficultyLevel.EASY);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(structuredOutputOptionsFactory.create(anyString(), anyMap())).thenReturn(mock(ChatOptions.class));
    }

    @Test
    void publishesCompletedEventWhenAiReturnsValidPayload() {
        when(callResponseSpec.content())
                .thenReturn(
                        """
                        {"questions":[{"statement":"Quanto é 2+2?","options":["3","4"],"correctOptionIndex":1}]}
                        """);

        service.generate(event);

        ArgumentCaptor<ExamGenerationCompletedEvent> captor =
                ArgumentCaptor.forClass(ExamGenerationCompletedEvent.class);
        verify(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY_COMPLETED), captor.capture());
        ExamGenerationCompletedEvent completed = captor.getValue();
        assertThat(completed.examId()).isEqualTo(event.examId());
        assertThat(completed.questions()).hasSize(1);
        assertThat(completed.questions().get(0).statement()).isEqualTo("Quanto é 2+2?");
        assertThat(completed.questions().get(0).correctOptionIndex()).isEqualTo(1);
    }

    @Test
    void publishesFailedEventWhenQuestionCountDoesNotMatch() {
        when(callResponseSpec.content())
                .thenReturn(
                        """
                        {"questions":[
                          {"statement":"Q1","options":["a","b"],"correctOptionIndex":0},
                          {"statement":"Q2","options":["a","b"],"correctOptionIndex":0}
                        ]}
                        """);

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.examId()).isEqualTo(event.examId());
        assertThat(failed.reason()).isEqualTo(FailureReason.INVALID_RESPONSE);
        assertThat(failed.message()).contains("Expected 1 questions but got 2");
    }

    @Test
    void publishesFailedEventWhenCorrectOptionIndexOutOfRange() {
        when(callResponseSpec.content())
                .thenReturn(
                        """
                        {"questions":[{"statement":"Q1","options":["a","b"],"correctOptionIndex":5}]}
                        """);

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.reason()).isEqualTo(FailureReason.INVALID_RESPONSE);
        assertThat(failed.message()).contains("out-of-range correctOptionIndex");
    }

    @Test
    void publishesFailedEventWhenAiResponseIsNotValidJson() {
        when(callResponseSpec.content()).thenReturn("this is not json");

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.reason()).isEqualTo(FailureReason.INVALID_RESPONSE);
    }

    @Test
    void classifiesQuotaExceededFrom429Error() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException("429 Too Many Requests"));

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.reason()).isEqualTo(FailureReason.QUOTA_EXCEEDED);
    }

    @Test
    void classifiesLlmErrorForOtherNonTransientFailures() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException("500 Internal Server Error"));

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.reason()).isEqualTo(FailureReason.LLM_ERROR);
    }

    @Test
    void classifiesTimeoutForTransientFailures() {
        when(callResponseSpec.content()).thenThrow(new TransientAiException("connection reset"));

        service.generate(event);

        ExamGenerationFailedEvent failed = captureFailedEvent();
        assertThat(failed.reason()).isEqualTo(FailureReason.TIMEOUT);
    }

    private ExamGenerationFailedEvent captureFailedEvent() {
        ArgumentCaptor<ExamGenerationFailedEvent> captor = ArgumentCaptor.forClass(ExamGenerationFailedEvent.class);
        verify(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY_FAILED), captor.capture());
        return captor.getValue();
    }
}
