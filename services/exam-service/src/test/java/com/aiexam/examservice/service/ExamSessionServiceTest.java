package com.aiexam.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.examservice.dto.ExamSessionResponse;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.exception.ExamSessionNotFoundException;
import com.aiexam.examservice.exception.InvalidExamStateException;
import com.aiexam.examservice.repository.ExamRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ExamSessionServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ExamRepository examRepository;

    private ExamSessionService examSessionService;

    private ExamSessionService service() {
        return new ExamSessionService(redisTemplate, examRepository);
    }

    private Exam readyExam(UUID examId, int durationMinutes) {
        return Exam.builder()
                .id(examId)
                .theme("Basic Arithmetic")
                .questionCount(2)
                .difficulty(DifficultyLevel.EASY)
                .durationMinutes(durationMinutes)
                .status(ExamStatus.READY)
                .build();
    }

    @Test
    void startSessionCreatesTtlBackedSessionForReadyExam() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExam(examId, 30);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String startedAt = Instant.now().toString();
        when(valueOperations.get(anyString())).thenReturn(startedAt);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(1800L);

        examSessionService = service();
        ExamSessionResponse response = examSessionService.startSession(examId, "ada@example.com");

        assertThat(response.examId()).isEqualTo(examId);
        assertThat(response.startedAt()).isEqualTo(Instant.parse(startedAt));
        assertThat(response.remainingSeconds()).isEqualTo(1800L);
        String expectedKey = "exam-session:" + examId + ":ada@example.com";
        org.mockito.Mockito.verify(valueOperations)
                .setIfAbsent(eq(expectedKey), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void startSessionIsIdempotentWhenSessionAlreadyActive() {
        UUID examId = UUID.randomUUID();
        Exam exam = readyExam(examId, 30);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        String startedAt = Instant.now().minusSeconds(60).toString();
        when(valueOperations.get(anyString())).thenReturn(startedAt);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(1740L);

        examSessionService = service();
        ExamSessionResponse response = examSessionService.startSession(examId, "ada@example.com");

        assertThat(response.startedAt()).isEqualTo(Instant.parse(startedAt));
        assertThat(response.remainingSeconds()).isEqualTo(1740L);
    }

    @Test
    void startSessionThrowsWhenExamNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        examSessionService = service();
        assertThatThrownBy(() -> examSessionService.startSession(examId, "ada@example.com"))
                .isInstanceOf(ExamNotFoundException.class);
    }

    @Test
    void startSessionThrowsWhenExamNotReady() {
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

        examSessionService = service();
        assertThatThrownBy(() -> examSessionService.startSession(examId, "ada@example.com"))
                .isInstanceOf(InvalidExamStateException.class);
    }

    @Test
    void getSessionReturnsRemainingTimeForActiveSession() {
        UUID examId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String startedAt = Instant.now().minusSeconds(120).toString();
        when(valueOperations.get(anyString())).thenReturn(startedAt);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(1680L);

        examSessionService = service();
        ExamSessionResponse response = examSessionService.getSession(examId, "ada@example.com");

        assertThat(response.remainingSeconds()).isEqualTo(1680L);
    }

    @Test
    void getSessionThrowsWhenNoActiveSessionExists() {
        UUID examId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        examSessionService = service();
        assertThatThrownBy(() -> examSessionService.getSession(examId, "ada@example.com"))
                .isInstanceOf(ExamSessionNotFoundException.class);
    }

    @Test
    void endSessionDeletesTheSessionKey() {
        UUID examId = UUID.randomUUID();

        examSessionService = service();
        examSessionService.endSession(examId, "ada@example.com");

        org.mockito.Mockito.verify(redisTemplate).delete("exam-session:" + examId + ":ada@example.com");
    }
}
