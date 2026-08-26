package com.aiexam.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(UUID id, String provider, Instant createdAt) {}
