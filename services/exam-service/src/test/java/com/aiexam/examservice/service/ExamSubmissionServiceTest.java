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
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));
        when(examSubmissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmissionResponse response =
                service()
                        .submit(
                                examId,
                                "ada@example.com",
                                new SubmitExamRequest(List.of(1, 1), List.of(true, false)));

        assertThat(response.examId()).isEqualTo(examId);
        assertThat(response.totalQuestions()).isEqualTo(2);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.scorePercentage()).isEqualTo(50.0);
        assertThat(response.flaggedQuestions()).containsExactly(true, false);
        verify(examSessionService).endSession(examId, "ada@example.com");
    }

    @Test
    void submitDefaultsFlagsToAllFalseWhenOmitted() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));
        when(examSubmissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmissionResponse response =
                service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 1), null));

        assertThat(response.flaggedQuestions()).containsExactly(false, false);
    }

    @Test
    void submitThrowsWhenFlagCountMismatches() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));

        assertThatThrownBy(
                        () ->
                                service()
                                        .submit(
                                                examId,
                                                "ada@example.com",
                                                new SubmitExamRequest(List.of(1, 1), List.of(true))))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void submitThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(0), null)))
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
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(0), null)))
                .isInstanceOf(InvalidExamStateException.class);
    }

    @Test
    void submitAllowsARetakeAfterAPriorAttempt() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));
        when(examSubmissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamSubmissionResponse response =
                service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 0), null));

        assertThat(response.correctCount()).isEqualTo(2);
        verify(examSubmissionRepository).save(any(ExamSubmission.class));
        verify(examSessionService).endSession(examId, "ada@example.com");
    }

    @Test
    void submitThrowsWhenSessionExpiredOrNeverStarted() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenThrow(new ExamSessionNotFoundException(examId));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1, 1), null)))
                .isInstanceOf(ExamSessionNotFoundException.class);
        verify(examSubmissionRepository, never()).save(any());
    }

    @Test
    void submitThrowsWhenAnswerCountMismatches() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examSessionService.getSession(examId, "ada@example.com"))
                .thenReturn(new ExamSessionResponse(examId, Instant.now(), 900L));

        assertThatThrownBy(
                        () -> service().submit(examId, "ada@example.com", new SubmitExamRequest(List.of(1), null)))
                .isInstanceOf(InvalidSubmissionException.class);
    }

    @Test
    void getSubmissionReturnsTheMostRecentAttempt() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        ExamSubmission submission =
                ExamSubmission.builder()
                        .exam(exam)
                        .studentEmail("ada@example.com")
                        .selectedOptions(List.of(1, 1))
                        .flaggedQuestions(List.of(false, true))
                        .correctCount(1)
                        .totalQuestions(2)
                        .scorePercentage(50.0)
                        .submittedAt(Instant.now())
                        .build();
        when(examSubmissionRepository.findFirstByExam_IdAndStudentEmailOrderBySubmittedAtDesc(
                        examId, "ada@example.com"))
                .thenReturn(Optional.of(submission));

        ExamSubmissionResponse response = service().getSubmission(examId, "ada@example.com");

        assertThat(response.examId()).isEqualTo(examId);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.scorePercentage()).isEqualTo(50.0);
        assertThat(response.flaggedQuestions()).containsExactly(false, true);
    }

    @Test
    void getSubmissionHistoryReturnsEveryAttemptMostRecentFirst() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        ExamSubmission first =
                ExamSubmission.builder()
                        .exam(exam)
                        .studentEmail("ada@example.com")
                        .selectedOptions(List.of(0, 0))
                        .correctCount(0)
                        .totalQuestions(2)
                        .scorePercentage(0.0)
                        .submittedAt(Instant.now().minusSeconds(60))
                        .build();
        ExamSubmission second =
                ExamSubmission.builder()
                        .exam(exam)
                        .studentEmail("ada@example.com")
                        .selectedOptions(List.of(1, 0))
                        .correctCount(2)
                        .totalQuestions(2)
                        .scorePercentage(100.0)
                        .submittedAt(Instant.now())
                        .build();
        when(examSubmissionRepository.findByExam_IdAndStudentEmailOrderBySubmittedAtDesc(
                        examId, "ada@example.com"))
                .thenReturn(List.of(second, first));

        var history = service().getSubmissionHistory(examId, "ada@example.com");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).scorePercentage()).isEqualTo(100.0);
        assertThat(history.get(1).scorePercentage()).isEqualTo(0.0);
    }

    @Test
    void listSubmissionsReturnsMappedSummariesForExam() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExamWithQuestions(examId);
        when(examRepository.existsById(examId)).thenReturn(true);
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
        when(examSubmissionRepository.findByExam_IdOrderByStudentEmailAscSubmittedAtDesc(examId))
                .thenReturn(List.of(submission));

        var summaries = service().listSubmissions(examId);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).studentEmail()).isEqualTo("ada@example.com");
        assertThat(summaries.get(0).scorePercentage()).isEqualTo(50.0);
    }

    @Test
    void hasSubmittedReturnsTrueWhenSubmissionExists() {
        UUID examId = UUID.randomUUID();
        when(examSubmissionRepository.existsByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(true);

        assertThat(service().hasSubmitted(examId, "ada@example.com")).isTrue();
    }

    @Test
    void hasSubmittedReturnsFalseWhenNoSubmission() {
        UUID examId = UUID.randomUUID();
        when(examSubmissionRepository.existsByExam_IdAndStudentEmail(examId, "ada@example.com"))
                .thenReturn(false);

        assertThat(service().hasSubmitted(examId, "ada@example.com")).isFalse();
    }

    @Test
    void listSubmissionsThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.existsById(examId)).thenReturn(false);

        assertThatThrownBy(() -> service().listSubmissions(examId)).isInstanceOf(ExamNotFoundException.class);
    }
}
