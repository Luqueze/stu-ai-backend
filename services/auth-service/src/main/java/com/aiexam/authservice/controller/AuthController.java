package com.aiexam.authservice.controller;

import com.aiexam.authservice.dto.ApiKeyResponse;
import com.aiexam.authservice.dto.AuthResponse;
import com.aiexam.authservice.dto.LoginRequest;
import com.aiexam.authservice.dto.RegisterRequest;
import com.aiexam.authservice.dto.SaveApiKeyRequest;
import com.aiexam.authservice.service.AiCredentialService;
import com.aiexam.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AiCredentialService aiCredentialService;

    @PostMapping("/api/v1/auth/register")
    @Operation(summary = "Registers a new user account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/api/v1/auth/login")
    @Operation(summary = "Authenticates a user and issues a JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/api/v1/users/me/api-keys")
    @Operation(summary = "Saves or updates the caller's AI provider API key")
    public ResponseEntity<ApiKeyResponse> saveApiKey(
            @Valid @RequestBody SaveApiKeyRequest request, Authentication authentication) {
        ApiKeyResponse response = aiCredentialService.saveApiKey(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/users/me/api-keys")
    @Operation(summary = "Lists the caller's saved AI provider API keys")
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(Authentication authentication) {
        return ResponseEntity.ok(aiCredentialService.listApiKeys(authentication.getName()));
    }
}
