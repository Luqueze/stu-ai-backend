package com.aiexam.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "xADi8Nsg/rZT5dkBSpt5OB+m9+frNVnwprnrsb+EYXU=";

    private final JwtService jwtService = new JwtService(SECRET);

    private String buildToken(String email, String role, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiresInMs)))
                .signWith(key)
                .compact();
    }

    @Test
    void validatesTokenSignedWithSameSecret() {
        String token = buildToken("ada@example.com", "STUDENT", 3_600_000L);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("ada@example.com");
        assertThat(jwtService.extractClaims(token).get("role", String.class)).isEqualTo("STUDENT");
    }

    @Test
    void rejectsTamperedToken() {
        String token = buildToken("ada@example.com", "STUDENT", 3_600_000L) + "tampered";

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        String token = buildToken("ada@example.com", "STUDENT", -1_000L);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
