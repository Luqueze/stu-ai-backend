package com.aiexam.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveApiKeyRequest(
        @NotBlank(message = "Provider is required") String provider,
        @NotBlank(message = "API key is required") String apiKey) {}
