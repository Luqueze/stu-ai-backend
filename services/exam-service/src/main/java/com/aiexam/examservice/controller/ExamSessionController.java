package com.aiexam.examservice.controller;

import com.aiexam.examservice.dto.ExamSessionResponse;
import com.aiexam.examservice.service.ExamSessionService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @PostMapping("/api/v1/exams/{examId}/session")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Starts the caller's timed session for a ready exam")
    public ResponseEntity<ExamSessionResponse> startSession(@PathVariable UUID examId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examSessionService.startSession(examId, currentEmail()));
    }

    @GetMapping("/api/v1/exams/{examId}/session")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Retrieves the remaining time for the caller's exam session")
    public ResponseEntity<ExamSessionResponse> getSession(@PathVariable UUID examId) {
        return ResponseEntity.ok(examSessionService.getSession(examId, currentEmail()));
    }

    private String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
