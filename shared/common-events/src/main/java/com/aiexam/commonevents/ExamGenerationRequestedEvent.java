package com.aiexam.commonevents;

import java.util.UUID;

public record ExamGenerationRequestedEvent(
        UUID examId, String theme, int questionCount, DifficultyLevel difficulty) {}
