package com.aiexam.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.authservice.dto.AuthResponse;
import com.aiexam.authservice.dto.LoginRequest;
import com.aiexam.authservice.dto.RegisterRequest;
import com.aiexam.authservice.entity.Role;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.EmailAlreadyExistsException;
import com.aiexam.authservice.repository.UserRepository;
import com.aiexam.authservice.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    @Test
    void registerCreatesUserAndReturnsAuthResponse() {
        RegisterRequest request = new RegisterRequest("Ada Lovelace", "ada@example.com", "password123");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User toSave = invocation.getArgument(0);
                            return User.builder()
                                    .id(UUID.randomUUID())
                                    .name(toSave.getName())
                                    .email(toSave.getEmail())
                                    .passwordHash(toSave.getPasswordHash())
                                    .role(toSave.getRole())
                                    .build();
                        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.user().role()).isEqualTo(Role.STUDENT.name());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("Ada", "ada@example.com", "password123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsAuthResponseForValidCredentials() {
        User user =
                User.builder()
                        .id(UUID.randomUUID())
                        .name("Ada")
                        .email("ada@example.com")
                        .passwordHash("hashed-password")
                        .role(Role.STUDENT)
                        .build();
        LoginRequest request = new LoginRequest("ada@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void loginThrowsBadCredentialsForWrongPassword() {
        User user =
                User.builder()
                        .id(UUID.randomUUID())
                        .name("Ada")
                        .email("ada@example.com")
                        .passwordHash("hashed-password")
                        .role(Role.STUDENT)
                        .build();
        LoginRequest request = new LoginRequest("ada@example.com", "wrong-password");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginThrowsBadCredentialsWhenEmailNotFound() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }
}
