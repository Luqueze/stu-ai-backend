package com.aiexam.examservice.exception;

import java.util.UUID;

public class ExamSubmissionNotFoundException extends RuntimeException {
    public ExamSubmissionNotFoundException(UUID examId) {
        super("No submission found for exam: " + examId);
    }
}
