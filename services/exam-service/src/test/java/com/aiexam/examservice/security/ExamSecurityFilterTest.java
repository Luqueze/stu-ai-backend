package com.aiexam.examservice.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.commonevents.DifficultyLevel;
import com.aiexam.examservice.controller.ExamController;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.service.ExamService;
import com.aiexam.examservice.service.ExamSessionService;
import com.aiexam.examservice.service.ExamSubmissionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ExamController.class)
@Import(SecurityConfig.class)
class ExamSecurityFilterTest {

    private static final String CREATE_EXAM_BODY =
            """
            {"theme":"Basic Arithmetic","questionCount":2,"difficulty":"EASY","durationMinutes":30}
            """;

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private ExamService examService;
    @MockBean private ExamSubmissionService examSubmissionService;
    @MockBean private ExamSessionService examSessionService;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token"));
        when(examService.createExam(any(), any()))
                .thenReturn(
                        new ExamResponse(
                                UUID.randomUUID(), "Basic Arithmetic", 2, DifficultyLevel.EASY, ExamStatus.PENDING,
                                null, null, 30, Instant.now(), List.of()));
    }

    private String tokenWithRoles(String... roles) {
        String tokenValue = "token-" + String.join("-", roles);
        Jwt jwt =
                Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .subject("2f1c4f0e-3b7a-4d4e-9a51-7c0f7c2b1d11")
                        .claim("email", "ada@example.com")
                        .claim("roles", List.of(roles))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
        when(jwtDecoder.decode(eq(tokenValue))).thenReturn(jwt);
        return tokenValue;
    }

    private ResultActions createExam(String token) throws Exception {
        var request = post("/api/v1/exams").contentType(MediaType.APPLICATION_JSON).content(CREATE_EXAM_BODY);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        createExam(null).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        createExam("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void allowsStudentToCreateExamUsingEmailClaimAsOwner() throws Exception {
        createExam(tokenWithRoles("STUDENT", "offline_access")).andExpect(status().isAccepted());

        verify(examService).createExam(any(), eq("ada@example.com"));
    }

    @Test
    void allowsAdminToCreateExam() throws Exception {
        createExam(tokenWithRoles("ADMIN")).andExpect(status().isAccepted());
    }

    @Test
    void forbidsTokenWithoutPlatformRole() throws Exception {
        createExam(tokenWithRoles("offline_access")).andExpect(status().isForbidden());
    }
}
