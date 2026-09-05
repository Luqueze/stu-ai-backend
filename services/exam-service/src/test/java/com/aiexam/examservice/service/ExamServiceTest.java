package com.aiexam.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import com.aiexam.commonevents.FailureReason;
import com.aiexam.commonevents.GeneratedQuestion;
import com.aiexam.examservice.config.RabbitMQConfig;
import com.aiexam.examservice.dto.CreateExamRequest;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.repository.ExamRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock private ExamRepository examRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private ExamService examService;

    @Test
    void createExamSavesPendingExamAndPublishesRequestedEvent() {
        CreateExamRequest request = new CreateExamRequest("Basic Arithmetic", 2, DifficultyLevel.EASY);
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(
                        invocation -> {
                            Exam toSave = invocation.getArgument(0);
                            return Exam.builder()
                                    .id(UUID.randomUUID())
                                    .theme(toSave.getTheme())
                                    .questionCount(toSave.getQuestionCount())
                                    .difficulty(toSave.getDifficulty())
                                    .status(toSave.getStatus())
                                    .build();
                        });

        ExamResponse response = examService.createExam(request);

        assertThat(response.status()).isEqualTo(ExamStatus.PENDING);
        assertThat(response.theme()).isEqualTo("Basic Arithmetic");
        assertThat(response.questions()).isEmpty();

        ArgumentCaptor<ExamGenerationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(ExamGenerationRequestedEvent.class);
        verify(rabbitTemplate)
                .convertAndSend(
                        eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY_REQUESTED), eventCaptor.capture());
        ExamGenerationRequestedEvent event = eventCaptor.getValue();
        assertThat(event.examId()).isEqualTo(response.id());
        assertThat(event.theme()).isEqualTo("Basic Arithmetic");
        assertThat(event.questionCount()).isEqualTo(2);
        assertThat(event.difficulty()).isEqualTo(DifficultyLevel.EASY);
    }

    @Test
    void getExamReturnsMappedResponse() {
        UUID examId = UUID.randomUUID();
        Exam exam =
                Exam.builder()
                        .id(examId)
                        .theme("Capitais")
                        .questionCount(1)
                        .difficulty(DifficultyLevel.EASY)
                        .status(ExamStatus.PENDING)
                        .build();
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        ExamResponse response = examService.getExam(examId);

        assertThat(response.id()).isEqualTo(examId);
        assertThat(response.theme()).isEqualTo("Capitais");
    }

    @Test
    void getExamThrowsWhenNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.getExam(examId)).isInstanceOf(ExamNotFoundException.class);
    }

    @Test
    void listExamsMapsAllRepositoryEntries() {
        Exam examA =
                Exam.builder()
                        .id(UUID.randomUUID())
                        .theme("A")
                        .questionCount(1)
                        .difficulty(DifficultyLevel.EASY)
                        .status(ExamStatus.PENDING)
                        .build();
        Exam examB =
                Exam.builder()
                        .id(UUID.randomUUID())
                        .theme("B")
                        .questionCount(1)
                        .difficulty(DifficultyLevel.HARD)
                        .status(ExamStatus.READY)
                        .build();
        when(examRepository.findAll()).thenReturn(List.of(examA, examB));

        List<ExamResponse> responses = examService.listExams();

        assertThat(responses).hasSize(2).extracting(ExamResponse::theme).containsExactly("A", "B");
    }

    @Test
    void completeExamMarksExamReadyWithGeneratedQuestions() {
        UUID examId = UUID.randomUUID();
        Exam exam =
                Exam.builder()
                        .id(examId)
                        .theme("Cores")
                        .questionCount(1)
                        .difficulty(DifficultyLevel.EASY)
                        .status(ExamStatus.PENDING)
                        .build();
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        List<GeneratedQuestion> questions =
                List.of(new GeneratedQuestion("Qual a cor do céu?", List.of("Azul", "Verde"), 0));

        examService.completeExam(examId, questions);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.READY);
        assertThat(exam.getQuestions()).hasSize(1);
        assertThat(exam.getQuestions().get(0).getStatement()).isEqualTo("Qual a cor do céu?");
    }

    @Test
    void failExamMarksExamFailedWithReasonAndMessage() {
        UUID examId = UUID.randomUUID();
        Exam exam =
                Exam.builder()
                        .id(examId)
                        .theme("Cores")
                        .questionCount(1)
                        .difficulty(DifficultyLevel.EASY)
                        .status(ExamStatus.PENDING)
                        .build();
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        examService.failExam(examId, FailureReason.INVALID_RESPONSE, "bad response");

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.FAILED);
        assertThat(exam.getFailureReason()).isEqualTo(FailureReason.INVALID_RESPONSE);
        assertThat(exam.getFailureMessage()).isEqualTo("bad response");
    }
}
