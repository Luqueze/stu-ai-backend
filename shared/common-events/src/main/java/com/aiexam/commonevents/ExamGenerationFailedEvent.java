package com.aiexam.commonevents;

import java.util.UUID;

public record ExamGenerationFailedEvent(UUID examId, FailureReason reason, String message) {}
