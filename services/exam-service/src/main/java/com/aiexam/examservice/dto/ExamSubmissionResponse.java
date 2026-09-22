package com.aiexam.examservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExamSubmissionResponse(
        UUID examId,
        int totalQuestions,
        int correctCount,
        double scorePercentage,
        Instant submittedAt,
        List<Boolean> flaggedQuestions) {}
