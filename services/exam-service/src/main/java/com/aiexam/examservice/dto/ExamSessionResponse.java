package com.aiexam.examservice.dto;

import java.time.Instant;
import java.util.UUID;

public record ExamSessionResponse(UUID examId, Instant startedAt, long remainingSeconds) {}
