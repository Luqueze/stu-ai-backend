package com.aiexam.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiexam.authservice.entity.Role;
import com.aiexam.authservice.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "xADi8Nsg/rZT5dkBSpt5OB+m9+frNVnwprnrsb+EYXU=";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L);
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .name("Ada Lovelace")
                        .email("ada@example.com")
                        .passwordHash("hashed-password")
                        .role(Role.STUDENT)
                        .build();
    }

    @Test
    void generatesValidTokenWithEmailAndRoleClaims() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("ada@example.com");
        assertThat(jwtService.extractClaims(token).get("role", String.class)).isEqualTo("STUDENT");
        assertThat(jwtService.extractClaims(token).get("userId", String.class))
                .isEqualTo(user.getId().toString());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user) + "tampered";

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiringImmediately = new JwtService(SECRET, -1_000L);

        String token = expiringImmediately.generateToken(user);

        assertThat(expiringImmediately.isTokenValid(token)).isFalse();
    }
}
