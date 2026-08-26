package com.aiexam.authservice.service;

import com.aiexam.authservice.dto.AuthResponse;
import com.aiexam.authservice.dto.LoginRequest;
import com.aiexam.authservice.dto.RegisterRequest;
import com.aiexam.authservice.dto.UserResponse;
import com.aiexam.authservice.entity.Role;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.exception.EmailAlreadyExistsException;
import com.aiexam.authservice.repository.UserRepository;
import com.aiexam.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user =
                User.builder()
                        .name(request.name())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .role(Role.STUDENT)
                        .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        UserResponse userResponse =
                new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, jwtService.getExpirationMs() / 1000, userResponse);
    }
}
