package com.aiexam.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.authservice.entity.PasswordResetToken;
import com.aiexam.authservice.entity.Role;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.InvalidResetTokenException;
import com.aiexam.authservice.repository.PasswordResetTokenRepository;
import com.aiexam.authservice.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final long TOKEN_EXPIRATION_MS = 1_800_000L;
    private static final String FRONTEND_URL = "http://localhost:4200";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailService mailService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService =
                new PasswordResetService(
                        userRepository,
                        passwordResetTokenRepository,
                        passwordEncoder,
                        mailService,
                        TOKEN_EXPIRATION_MS,
                        FRONTEND_URL);
    }

    private User sampleUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Ada")
                .email("ada@example.com")
                .passwordHash("hashed-password")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    void requestResetSavesTokenAndSendsEmailWhenUserExists() {
        User user = sampleUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.requestReset(user.getEmail());

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetEmail(eq(user.getEmail()), anyString());
    }

    @Test
    void requestResetDoesNothingWhenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("missing@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPasswordUpdatesUserAndMarksTokenUsedForValidToken() {
        User user = sampleUser();
        PasswordResetToken validToken =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .tokenHash("hash")
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .build();
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hashed-password");

        passwordResetService.resetPassword("raw-token", "newPassword123");

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed-password");
        assertThat(validToken.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(validToken);
    }

    @Test
    void resetPasswordThrowsForUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("bogus-token", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPasswordThrowsForExpiredToken() {
        User user = sampleUser();
        PasswordResetToken expiredToken =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .tokenHash("hash")
                        .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                        .build();
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword("some-token", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordThrowsForAlreadyUsedToken() {
        User user = sampleUser();
        PasswordResetToken usedToken =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .tokenHash("hash")
                        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .usedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                        .build();
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword("some-token", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).save(any());
    }
}
