package com.aiexam.examservice.exception;

import java.util.UUID;

public class ExamNotFoundException extends RuntimeException {
    public ExamNotFoundException(UUID examId) {
        super("Exam not found: " + examId);
    }
}
