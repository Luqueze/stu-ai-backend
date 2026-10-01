package com.aiexam.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityFilterTest {

    private static final String VALID_TOKEN = "valid-token";

    @Autowired private WebTestClient webTestClient;

    @MockBean private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("Invalid token")));
        when(jwtDecoder.decode(eq(VALID_TOKEN)))
                .thenReturn(
                        Mono.just(
                                Jwt.withTokenValue(VALID_TOKEN)
                                        .header("alg", "RS256")
                                        .subject("2f1c4f0e-3b7a-4d4e-9a51-7c0f7c2b1d11")
                                        .claim("email", "ada@example.com")
                                        .claim("roles", List.of("STUDENT"))
                                        .issuedAt(Instant.now())
                                        .expiresAt(Instant.now().plusSeconds(300))
                                        .build()));
    }

    @Test
    void rejectsProtectedRouteWithoutToken() {
        webTestClient.get().uri("/api/v1/exams").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsProtectedRouteWithInvalidToken() {
        webTestClient
                .get()
                .uri("/api/v1/exams")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void letsValidTokenPastTheGatewaySecurityFilter() {
        webTestClient
                .get()
                .uri("/api/v1/exams")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void permitsSwaggerUiWithoutToken() {
        webTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().isFound();
    }

    @Test
    void permitsApiDocsWithoutToken() {
        webTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
    }
}
