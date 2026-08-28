package com.aiexam.examservice.dto;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.commonevents.FailureReason;
import com.aiexam.examservice.entity.ExamStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExamResponse(
        UUID id,
        String theme,
        int questionCount,
        DifficultyLevel difficulty,
        ExamStatus status,
        FailureReason failureReason,
        String failureMessage,
        Instant createdAt,
        List<ExamQuestionResponse> questions) {}
