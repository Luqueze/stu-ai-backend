package com.aiexam.aigeneratorservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GeneratedQuestionPayload(
        @JsonProperty(required = true) String statement,
        @JsonProperty(required = true) List<String> options,
        @JsonProperty(required = true) int correctOptionIndex) {}
