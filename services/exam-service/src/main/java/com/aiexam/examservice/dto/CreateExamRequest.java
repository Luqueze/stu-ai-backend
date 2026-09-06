package com.aiexam.examservice.dto;

import com.aiexam.commonevents.DifficultyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExamRequest(
        @NotBlank(message = "Theme is required") String theme,
        @Min(value = 1, message = "At least 1 question required")
                @Max(value = 50, message = "At most 50 questions allowed")
                int questionCount,
        @NotNull(message = "Difficulty is required") DifficultyLevel difficulty,
        @Min(value = 1, message = "Duration must be at least 1 minute")
                @Max(value = 480, message = "Duration cannot exceed 480 minutes")
                int durationMinutes) {}
