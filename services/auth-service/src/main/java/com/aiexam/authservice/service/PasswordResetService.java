package com.aiexam.authservice.service;

import com.aiexam.authservice.entity.PasswordResetToken;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.InvalidResetTokenException;
import com.aiexam.authservice.repository.PasswordResetTokenRepository;
import com.aiexam.authservice.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final long tokenExpirationMs;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            MailService mailService,
            @Value("${app.password-reset.token-expiration-ms}") long tokenExpirationMs,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.tokenExpirationMs = tokenExpirationMs;
        this.frontendUrl = frontendUrl;
    }

    public void requestReset(String email) {
        userRepository
                .findByEmail(email)
                .ifPresent(
                        user -> {
                            String rawToken = generateRawToken();
                            PasswordResetToken token =
                                    PasswordResetToken.builder()
                                            .user(user)
                                            .tokenHash(hash(rawToken))
                                            .expiresAt(Instant.now().plus(tokenExpirationMs, ChronoUnit.MILLIS))
                                            .build();
                            passwordResetTokenRepository.save(token);

                            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
                            mailService.sendPasswordResetEmail(user.getEmail(), resetLink);
                        });
    }

    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHash(hash(rawToken))
                        .orElseThrow(InvalidResetTokenException::new);

        if (token.isExpired() || token.isUsed()) {
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.markUsed();
        passwordResetTokenRepository.save(token);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
