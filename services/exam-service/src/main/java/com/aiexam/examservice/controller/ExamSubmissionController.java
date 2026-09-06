package com.aiexam.examservice.controller;

import com.aiexam.examservice.dto.ExamSubmissionResponse;
import com.aiexam.examservice.dto.ExamSubmissionSummaryResponse;
import com.aiexam.examservice.dto.SubmitExamRequest;
import com.aiexam.examservice.service.ExamSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExamSubmissionController {

    private final ExamSubmissionService examSubmissionService;

    @PostMapping("/api/v1/exams/{examId}/submission")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submits the caller's answers and scores the exam")
    public ResponseEntity<ExamSubmissionResponse> submit(
            @PathVariable UUID examId, @Valid @RequestBody SubmitExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examSubmissionService.submit(examId, currentEmail(), request));
    }

    @GetMapping("/api/v1/exams/{examId}/submission")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Retrieves the caller's exam result")
    public ResponseEntity<ExamSubmissionResponse> getSubmission(@PathVariable UUID examId) {
        return ResponseEntity.ok(examSubmissionService.getSubmission(examId, currentEmail()));
    }

    @GetMapping("/api/v1/exams/{examId}/submissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lists every student's result for an exam")
    public ResponseEntity<List<ExamSubmissionSummaryResponse>> listSubmissions(@PathVariable UUID examId) {
        return ResponseEntity.ok(examSubmissionService.listSubmissions(examId));
    }

    private String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
