package com.aiexam.examservice.dto;

import java.time.Instant;

public record ExamSubmissionSummaryResponse(
        String studentEmail, int totalQuestions, int correctCount, double scorePercentage, Instant submittedAt) {}
