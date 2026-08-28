package com.aiexam.examservice.dto;

import java.util.List;

public record ExamQuestionResponse(String statement, List<String> options, int correctOptionIndex) {}
