package com.aiexam.aigeneratorservice.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record GeneratedExamPayload(
        @JsonPropertyDescription("The list of generated multiple-choice questions")
                List<GeneratedQuestionPayload> questions) {}
