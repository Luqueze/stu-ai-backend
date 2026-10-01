package com.aiexam.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiexam.authservice.dto.UserResponse;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.EmailNotVerifiedException;
import com.aiexam.authservice.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("2f1c4f0e-3b7a-4d4e-9a51-7c0f7c2b1d11");

    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    private Jwt jwt(boolean emailVerified) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(KEYCLOAK_ID.toString())
                .claim("email", "ada@example.com")
                .claim("name", "Ada Lovelace")
                .claim("email_verified", emailVerified)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    void returnsExistingUserAndSyncsProfileFromToken() {
        User existing = User.builder().id(UUID.randomUUID()).keycloakId(KEYCLOAK_ID).name("Ada").email("old@example.com").build();
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));

        User user = userService.resolveCurrentUser(jwt(true));

        assertThat(user).isSameAs(existing);
        assertThat(user.getName()).isEqualTo("Ada Lovelace");
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void provisionsNewUserOnFirstAccess() {
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndKeycloakIdIsNull("ada@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.resolveCurrentUser(jwt(true));

        assertThat(user.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(user.getName()).isEqualTo("Ada Lovelace");
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void linksLegacyAccountWhenEmailIsVerified() {
        UUID legacyId = UUID.randomUUID();
        User legacy = User.builder().id(legacyId).name("Ada").email("ada@example.com").build();
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndKeycloakIdIsNull("ada@example.com")).thenReturn(Optional.of(legacy));

        User user = userService.resolveCurrentUser(jwt(true));

        assertThat(user.getId()).isEqualTo(legacyId);
        assertThat(user.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesToLinkLegacyAccountWhenEmailIsNotVerified() {
        User legacy = User.builder().id(UUID.randomUUID()).name("Ada").email("ada@example.com").build();
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndKeycloakIdIsNull("ada@example.com")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> userService.resolveCurrentUser(jwt(false)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(legacy.getKeycloakId()).isNull();
    }

    @Test
    void reportsAdminRoleFromAuthorities() {
        User existing = User.builder().id(UUID.randomUUID()).keycloakId(KEYCLOAK_ID).name("Ada").email("ada@example.com").build();
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        var authentication =
                new JwtAuthenticationToken(
                        jwt(true), List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserResponse response = userService.getCurrentUser(authentication);

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.email()).isEqualTo("ada@example.com");
    }

    @Test
    void defaultsToStudentRole() {
        User existing = User.builder().id(UUID.randomUUID()).keycloakId(KEYCLOAK_ID).name("Ada").email("ada@example.com").build();
        when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(existing));
        var authentication = new JwtAuthenticationToken(jwt(true), List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        assertThat(userService.getCurrentUser(authentication).role()).isEqualTo("STUDENT");
    }
}
