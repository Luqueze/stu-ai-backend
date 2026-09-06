package com.aiexam.examservice.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.examservice.controller.ExamController;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.service.ExamService;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExamController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class ExamSecurityFilterTest {

    private static final String SECRET = "xADi8Nsg/rZT5dkBSpt5OB+m9+frNVnwprnrsb+EYXU=";

    @Autowired private MockMvc mockMvc;

    @MockBean private ExamService examService;

    private String tokenWithRole(String role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("ada@example.com")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(3_600_000L)))
                .signWith(key)
                .compact();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(
                        post("/api/v1/exams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"theme":"Basic Arithmetic","questionCount":2,"difficulty":"EASY"}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsStudentFromCreatingExam() throws Exception {
        mockMvc.perform(
                        post("/api/v1/exams")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("STUDENT"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"theme":"Basic Arithmetic","questionCount":2,"difficulty":"EASY","durationMinutes":30}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminToCreateExam() throws Exception {
        UUID examId = UUID.randomUUID();
        ExamResponse response =
                new ExamResponse(
                        examId, "Basic Arithmetic", 2, DifficultyLevel.EASY, ExamStatus.PENDING, null, null, 30,
                        Instant.now(), List.of());
        when(examService.createExam(any())).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/exams")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"theme":"Basic Arithmetic","questionCount":2,"difficulty":"EASY","durationMinutes":30}
                                        """))
                .andExpect(status().isAccepted());
    }
}
