package com.aiexam.examservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.examservice.dto.ExamSessionResponse;
import com.aiexam.examservice.exception.ExamSessionNotFoundException;
import com.aiexam.examservice.exception.InvalidExamStateException;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.security.JwtService;
import com.aiexam.examservice.service.ExamSessionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExamSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JwtService.class)
class ExamSessionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ExamSessionService examSessionService;

    @Test
    @WithMockUser(roles = "STUDENT")
    void startSessionReturnsCreatedWithRemainingTime() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamSessionResponse response = new ExamSessionResponse(examId, Instant.now(), 1800L);
        when(examSessionService.startSession(eq(examId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/session", examId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examId").value(examId.toString()))
                .andExpect(jsonPath("$.remainingSeconds").value(1800));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void startSessionReturnsConflictWhenExamNotReady() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examSessionService.startSession(eq(examId), any()))
                .thenThrow(new InvalidExamStateException(examId, ExamStatus.PENDING));

        mockMvc.perform(post("/api/v1/exams/{examId}/session", examId)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getSessionReturnsRemainingTime() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamSessionResponse response = new ExamSessionResponse(examId, Instant.now(), 900L);
        when(examSessionService.getSession(eq(examId), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/exams/{examId}/session", examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(900));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getSessionReturnsNotFoundWhenNoActiveSession() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examSessionService.getSession(eq(examId), any()))
                .thenThrow(new ExamSessionNotFoundException(examId));

        mockMvc.perform(get("/api/v1/exams/{examId}/session", examId)).andExpect(status().isNotFound());
    }
}
