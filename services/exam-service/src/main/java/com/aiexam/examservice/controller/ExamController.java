package com.aiexam.examservice.controller;

import com.aiexam.examservice.dto.CreateExamRequest;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PostMapping("/api/v1/exams")
    @Operation(summary = "Creates a new exam and queues AI question generation")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(examService.createExam(request));
    }

    @GetMapping("/api/v1/exams/{id}")
    @Operation(summary = "Retrieves an exam and its generated questions if ready")
    public ResponseEntity<ExamResponse> getExam(@PathVariable UUID id) {
        return ResponseEntity.ok(examService.getExam(id));
    }

    @GetMapping("/api/v1/exams")
    @Operation(summary = "Lists all exams")
    public ResponseEntity<List<ExamResponse>> listExams() {
        return ResponseEntity.ok(examService.listExams());
    }
}
