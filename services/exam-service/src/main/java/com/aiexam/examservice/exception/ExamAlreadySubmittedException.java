package com.aiexam.examservice.exception;

import java.util.UUID;

public class ExamAlreadySubmittedException extends RuntimeException {
    public ExamAlreadySubmittedException(UUID examId) {
        super("Exam " + examId + " has already been submitted by this student");
    }
}
