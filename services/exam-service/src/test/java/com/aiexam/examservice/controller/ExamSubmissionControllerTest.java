package com.aiexam.examservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.examservice.dto.ExamSubmissionResponse;
import com.aiexam.examservice.exception.ExamAlreadySubmittedException;
import com.aiexam.examservice.exception.ExamSubmissionNotFoundException;
import com.aiexam.examservice.exception.InvalidSubmissionException;
import com.aiexam.examservice.security.JwtService;
import com.aiexam.examservice.service.ExamSubmissionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExamSubmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JwtService.class)
class ExamSubmissionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ExamSubmissionService examSubmissionService;

    @Test
    @WithMockUser(roles = "STUDENT")
    void submitReturnsCreatedWithScore() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamSubmissionResponse response = new ExamSubmissionResponse(examId, 2, 1, 50.0, Instant.now());
        when(examSubmissionService.submit(eq(examId), any(), any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/exams/{examId}/submission", examId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"selectedOptions":[1,1]}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.scorePercentage").value(50.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void submitReturnsConflictWhenAlreadySubmitted() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examSubmissionService.submit(eq(examId), any(), any()))
                .thenThrow(new ExamAlreadySubmittedException(examId));

        mockMvc.perform(
                        post("/api/v1/exams/{examId}/submission", examId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"selectedOptions":[1,1]}
                                        """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void submitReturnsBadRequestWhenAnswersMismatch() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examSubmissionService.submit(eq(examId), any(), any()))
                .thenThrow(new InvalidSubmissionException(examId, "Expected 2 answers but got 1"));

        mockMvc.perform(
                        post("/api/v1/exams/{examId}/submission", examId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"selectedOptions":[1]}
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void submitWithEmptyAnswersReturnsBadRequest() throws Exception {
        UUID examId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/exams/{examId}/submission", examId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"selectedOptions":[]}
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getSubmissionReturnsScore() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamSubmissionResponse response = new ExamSubmissionResponse(examId, 2, 2, 100.0, Instant.now());
        when(examSubmissionService.getSubmission(eq(examId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/exams/{examId}/submission", examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercentage").value(100.0));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getSubmissionReturnsNotFoundWhenNoneExists() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examSubmissionService.getSubmission(eq(examId), any()))
                .thenThrow(new ExamSubmissionNotFoundException(examId));

        mockMvc.perform(get("/api/v1/exams/{examId}/submission", examId)).andExpect(status().isNotFound());
    }
}
