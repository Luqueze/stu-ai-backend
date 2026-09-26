package com.aiexam.aigeneratorservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record GeneratedQuestionPayload(
        @JsonProperty(required = true)
                @JsonPropertyDescription(
                        "Full question statement with context or scenario (3 to 6 sentences) ending in a clear question")
                String statement,
        @JsonProperty(required = true)
                @JsonPropertyDescription("Exactly 4 complete, plausible answer options without letter prefixes")
                List<String> options,
        @JsonProperty(required = true)
                @JsonPropertyDescription("Zero-based index (0-3) of the only correct option")
                int correctOptionIndex) {}
