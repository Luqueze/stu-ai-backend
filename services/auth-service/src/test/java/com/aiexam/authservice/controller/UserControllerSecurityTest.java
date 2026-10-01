package com.aiexam.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiexam.authservice.dto.UserResponse;
import com.aiexam.authservice.exception.EmailNotVerifiedException;
import com.aiexam.authservice.security.SecurityConfig;
import com.aiexam.authservice.service.AiCredentialService;
import com.aiexam.authservice.service.UserService;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    private static final String TOKEN = "valid-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private UserService userService;
    @MockBean private AiCredentialService aiCredentialService;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Invalid token"));
        when(jwtDecoder.decode(eq(TOKEN)))
                .thenReturn(
                        Jwt.withTokenValue(TOKEN)
                                .header("alg", "RS256")
                                .subject("2f1c4f0e-3b7a-4d4e-9a51-7c0f7c2b1d11")
                                .claim("email", "ada@example.com")
                                .claim("roles", List.of("ADMIN", "STUDENT"))
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(300))
                                .build());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsProfileWithRolesMappedFromTokenClaim() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUser(
                        argThat(auth -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))))
                .thenReturn(new UserResponse(userId, "Ada Lovelace", "ada@example.com", "ADMIN"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void returnsForbiddenWhenLegacyAccountEmailIsNotVerified() throws Exception {
        when(userService.getCurrentUser(any())).thenThrow(new EmailNotVerifiedException("ada@example.com"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }
}
