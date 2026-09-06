package com.aiexam.examservice.service;

import com.aiexam.examservice.dto.ExamSessionResponse;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.exception.ExamSessionNotFoundException;
import com.aiexam.examservice.exception.InvalidExamStateException;
import com.aiexam.examservice.repository.ExamRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExamSessionService {

    private static final String SESSION_KEY_PREFIX = "exam-session:";

    private final StringRedisTemplate redisTemplate;
    private final ExamRepository examRepository;

    public ExamSessionResponse startSession(UUID examId, String studentEmail) {
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new ExamNotFoundException(examId));
        if (exam.getStatus() != ExamStatus.READY) {
            throw new InvalidExamStateException(examId, exam.getStatus());
        }

        String key = sessionKey(examId, studentEmail);
        redisTemplate
                .opsForValue()
                .setIfAbsent(key, Instant.now().toString(), Duration.ofMinutes(exam.getDurationMinutes()));

        return currentSession(examId, key);
    }

    public ExamSessionResponse getSession(UUID examId, String studentEmail) {
        return currentSession(examId, sessionKey(examId, studentEmail));
    }

    private ExamSessionResponse currentSession(UUID examId, String key) {
        String startedAt = redisTemplate.opsForValue().get(key);
        if (startedAt == null) {
            throw new ExamSessionNotFoundException(examId);
        }

        Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return new ExamSessionResponse(
                examId, Instant.parse(startedAt), remainingSeconds == null ? 0 : Math.max(remainingSeconds, 0));
    }

    private String sessionKey(UUID examId, String studentEmail) {
        return SESSION_KEY_PREFIX + examId + ":" + studentEmail;
    }
}
