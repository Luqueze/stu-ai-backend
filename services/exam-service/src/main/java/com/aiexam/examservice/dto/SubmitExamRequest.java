package com.aiexam.examservice.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubmitExamRequest(
        @NotEmpty(message = "Answers are required") List<Integer> selectedOptions,
        // Optional: which questions the student flagged for review. Null/omitted means none were flagged.
        List<Boolean> flaggedQuestions) {}
