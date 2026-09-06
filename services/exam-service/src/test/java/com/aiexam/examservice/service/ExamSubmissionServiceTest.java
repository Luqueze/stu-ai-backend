package com.aiexam.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.examservice.dto.ExamSessionResponse;
import com.aiexam.examservice.dto.ExamSubmissionResponse;
import com.aiexam.examservice.dto.SubmitExamRequest;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamQuestion;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.entity.ExamSubmission;
import com.aiexam.examservice.exception.ExamAlreadySubmittedException;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.exception.ExamSessionNotFoundException;
import com.aiexam.examservice.exception.InvalidExamStateException;
import com.aiexam.examservice.exception.InvalidSubmissionException;
import com.aiexam.examservice.repository.ExamRepository;
import com.aiexam.examservice.repository.ExamSubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExamSubmissionServiceTest {

    @Mock private ExamRepository examRepository;
    @Mock private ExamSubmissionRepository examSubmissionRepository;
    @Mock private ExamSessionService examSessionService;

    private ExamSubmissionService service() {
        return new ExamSubmissionService(examRepository, examSubmissionRepository, examSessionService);
    }

    private Exam readyExamWithQuestions(UUID examId) {
        Exam exam =
                Exam.builder()
                        .id(examId)
                        .theme("Basic Arithmetic")
                        .questionCount(2)
                        .difficulty(DifficultyLevel.EASY)
                        .durationMinutes(30)
                        .status(ExamStatus.READY)
                        .build();
        exam.markReady(
                List.of(
                        ExamQuestion.builder()
                                .exam(exam)
                                .statement("2 + 2 = ?")
                                .options(List.of("3", "4"))
                                .correctOptionIndex(1)
                                .build(),
                        ExamQuestion.builder()
                                .exam(exam)
                                .statement("3 + 3 = ?")
                                .options(List.of("6", "5"))
                                .correctOptionIndex(0)
                                .build()));
        return exam;
    }

    @Test
    void submitScoresAnswersAndEndsSession() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSubmissionRepository.findByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(Optional.empty());
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));
        when(examSubmissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmissionResponse response =
                service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 1)));

        assertThat(response.examId()).isEqualTo(examId);
        assertThat(response.totalQuestions()).isEqualTo(2);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.scorePercentage()).isEqualTo(50.0);
        verify(examSessionService).endSession(examId, "ada@example.com");
    }

    @Test
    void submitThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(0))))
                .isInstanceOf(ExamNotFoundException.class);
    }

    @Test
    void submitThrowsWhenExamNotReady() {
        UUID examId = UUID.randomUUID();
        Exam exam =
                Exam.builder()
                        .id(examId)
                        .theme("Basic Arithmetic")
                        .questionCount(2)
                        .difficulty(DifficultyLevel.EASY)
                        .durationMinutes(30)
                        .status(ExamStatus.PENDING)
                        .build();
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(0))))
                .isInstanceOf(InvalidExamStateException.class);
    }

    @Test
    void submitThrowsWhenAlreadySubmitted() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSubmissionRepository.findByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(Optional.of(ExamSubmission.builder().build()));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 1))))
                .isInstanceOf(ExamAlreadySubmittedException.class);
    }

    @Test
    void submitThrowsWhenSessionExpiredOrNeverStarted() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSubmissionRepository.findByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(Optional.empty());
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenThrow(new ExamSessionNotFoundException(examId));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 1))))
                .isInstanceOf(ExamSessionNotFoundException.class);
        verify(examSubmissionRepository, never()).save(any());
    }

    @Test
    void submitThrowsWhenAnswerCountMismatches() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSubmissionRepository.findByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(Optional.empty());
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1))))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void getSubmissionReturnsMappedResponse() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        ExamSubmission submission =
                ExamSubmission.builder()
                        .exam(exam)
                        .studentEmail("ada@example.com")
                        .selectedOptions(List.of(1, 1))
                        .correctCount(1)
                        .totalQuestions(2)
                        .scorePercentage(50.0)
                        .submittedAt(Instant.now())
                        .build();
        when(examSubmissionRepository.findByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(Optional.of(submission));

        ExamSubmissionResponse response = service().getSubmission(examId, "ada@example.com");

        assertThat(response.examId()).isEqualTo(examId);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.scorePercentage()).isEqualTo(50.0);
    }
}
