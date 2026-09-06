package com.aiexam.examservice.exception;

import com.aiexam.examservice.entity.ExamStatus;
import java.util.UUID;

public class InvalidExamStateException extends RuntimeException {
    public InvalidExamStateException(UUID examId, ExamStatus status) {
        super("Exam " + examId + " is not ready to start a session (current status: " + status + ")");
    }
}
