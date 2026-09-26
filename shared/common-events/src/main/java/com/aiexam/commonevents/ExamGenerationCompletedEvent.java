package com.aiexam.commonevents;

import java.util.List;
import java.util.UUID;

public record ExamGenerationCompletedEvent(UUID examId, List<GeneratedQuestion> questions) {}
