package com.aiexam.examservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.examservice.dto.ExamQuestionResponse;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.security.JwtAuthenticationFilter;
import com.aiexam.examservice.security.JwtService;
import com.aiexam.examservice.security.SecurityConfig;
import com.aiexam.examservice.service.ExamService;
import com.aiexam.examservice.service.ExamSubmissionService;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(ExamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class ExamControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ExamService examService;
    @MockBean private ExamSubmissionService examSubmissionService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExamReturnsAcceptedWithBody() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamResponse response =
                new ExamResponse(
                        examId, "Basic Arithmetic", 2, DifficultyLevel.EASY, ExamStatus.PENDING, null, null, 30,
                        Instant.now(), List.of());
        when(examService.createExam(any(), any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/exams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"theme":"Basic Arithmetic","questionCount":2,"difficulty":"EASY","durationMinutes":30}
                                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(examId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExamWithBlankThemeReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/exams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"theme":"","questionCount":2,"difficulty":"EASY"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/exams"));
    }

    @Test
    void getExamReturnsNotFoundWhenExamMissing() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examService.getExam(examId)).thenThrow(new ExamNotFoundException(examId));

        mockMvc.perform(get("/api/v1/exams/{id}", examId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Exam not found: " + examId));
    }

    private ExamResponse readyExamWithOneQuestion(UUID examId) {
        return new ExamResponse(
                examId,
                "Basic Arithmetic",
                1,
                DifficultyLevel.EASY,
                ExamStatus.READY,
                null,
                null,
                30,
                Instant.now(),
                List.of(new ExamQuestionResponse("2 + 2 = ?", List.of("3", "4"), 1)));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getExamHidesAnswerKeyForStudent() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examService.getExam(examId)).thenReturn(readyExamWithOneQuestion(examId));

        mockMvc.perform(get("/api/v1/exams/{id}", examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExamShowsAnswerKeyForAdmin() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examService.getExam(examId)).thenReturn(readyExamWithOneQuestion(examId));

        mockMvc.perform(get("/api/v1/exams/{id}", examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").value(1));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getExamShowsAnswerKeyForStudentWhoAlreadySubmitted() throws Exception {
        UUID examId = UUID.randomUUID();
        when(examService.getExam(examId)).thenReturn(readyExamWithOneQuestion(examId));
        when(examSubmissionService.hasSubmitted(examId, "user")).thenReturn(true);

        mockMvc.perform(get("/api/v1/exams/{id}", examId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").value(1));
    }
}
