package com.aiexam.authservice.service;

import com.aiexam.authservice.dto.UserResponse;
import com.aiexam.authservice.entity.Role;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.EmailNotVerifiedException;
import com.aiexam.authservice.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ADMIN_AUTHORITY = "ROLE_" + Role.ADMIN.name();

    private final UserRepository userRepository;

    @Transactional
    public UserResponse getCurrentUser(JwtAuthenticationToken authentication) {
        User user = resolveCurrentUser(authentication.getToken());
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), resolveRole(authentication).name());
    }

    @Transactional
    public User resolveCurrentUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString(StandardClaimNames.EMAIL);
        String name = Optional.ofNullable(jwt.getClaimAsString(StandardClaimNames.NAME)).orElse(email);

        User user =
                userRepository
                        .findByKeycloakId(keycloakId)
                        .or(() -> linkLegacyAccount(jwt, keycloakId, email))
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                User.builder().keycloakId(keycloakId).name(name).email(email).build()));
        user.syncProfile(name, email);
        return user;
    }

    private Optional<User> linkLegacyAccount(Jwt jwt, UUID keycloakId, String email) {
        return userRepository
                .findByEmailAndKeycloakIdIsNull(email)
                .map(
                        legacyUser -> {
                            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED))) {
                                throw new EmailNotVerifiedException(email);
                            }
                            log.info("Linking legacy account {} to Keycloak user {}", legacyUser.getId(), keycloakId);
                            legacyUser.linkKeycloakAccount(keycloakId);
                            return legacyUser;
                        });
    }

    private Role resolveRole(JwtAuthenticationToken authentication) {
        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(ADMIN_AUTHORITY::equals);
        return isAdmin ? Role.ADMIN : Role.STUDENT;
    }
}
