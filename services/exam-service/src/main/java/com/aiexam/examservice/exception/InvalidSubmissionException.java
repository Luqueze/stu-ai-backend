package com.aiexam.examservice.exception;

import java.util.UUID;

public class InvalidSubmissionException extends RuntimeException {
    public InvalidSubmissionException(UUID examId, String message) {
        super("Invalid submission for exam " + examId + ": " + message);
    }
}
