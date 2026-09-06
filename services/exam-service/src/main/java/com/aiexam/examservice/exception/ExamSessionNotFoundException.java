package com.aiexam.examservice.exception;

import java.util.UUID;

public class ExamSessionNotFoundException extends RuntimeException {
    public ExamSessionNotFoundException(UUID examId) {
        super("No active session found for exam: " + examId);
    }
}
