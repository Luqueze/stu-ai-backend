package com.aiexam.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityFilterTest {

    private static final String SECRET = "xADi8Nsg/rZT5dkBSpt5OB+m9+frNVnwprnrsb+EYXU=";

    @Autowired private WebTestClient webTestClient;

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
    void rejectsProtectedRouteWithoutToken() {
        webTestClient
                .get()
                .uri("/api/v1/exams")
                .exchange()
                .expectStatus()
                .isUnauthorized();
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
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("STUDENT"))
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void permitsAuthRouteWithoutToken() {
        webTestClient
                .post()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }
}
