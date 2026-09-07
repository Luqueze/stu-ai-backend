package com.aiexam.examservice.controller;

import com.aiexam.examservice.dto.CreateExamRequest;
import com.aiexam.examservice.dto.ExamQuestionResponse;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.service.ExamService;
import com.aiexam.examservice.service.ExamSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamSubmissionService examSubmissionService;

    @PostMapping("/api/v1/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    @Operation(summary = "Creates a new exam and queues AI question generation")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(examService.createExam(request, currentEmail()));
    }

    @GetMapping("/api/v1/exams/{id}")
    @Operation(summary = "Retrieves an exam and its generated questions if ready")
    public ResponseEntity<ExamResponse> getExam(@PathVariable UUID id) {
        ExamResponse response = examService.getExam(id);
        if (isAdmin() || examSubmissionService.hasSubmitted(id, currentEmail())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(redactAnswerKey(response));
    }

    @GetMapping("/api/v1/exams")
    @Operation(summary = "Lists the caller's exams, or every exam for admins")
    public ResponseEntity<List<ExamResponse>> listExams() {
        return ResponseEntity.ok(
                examService.listExams(currentEmail(), isAdmin()).stream()
                        .map(this::redactAnswerKeyUnlessAdmin)
                        .toList());
    }

    private String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private ExamResponse redactAnswerKeyUnlessAdmin(ExamResponse response) {
        if (isAdmin()) {
            return response;
        }
        return redactAnswerKey(response);
    }

    private ExamResponse redactAnswerKey(ExamResponse response) {
        List<ExamQuestionResponse> redactedQuestions =
                response.questions().stream()
                        .map(q -> new ExamQuestionResponse(q.statement(), q.options(), null))
                        .toList();
        return new ExamResponse(
                response.id(),
                response.theme(),
                response.questionCount(),
                response.difficulty(),
                response.status(),
                response.failureReason(),
                response.failureMessage(),
                response.durationMinutes(),
                response.createdAt(),
                redactedQuestions);
    }
}
