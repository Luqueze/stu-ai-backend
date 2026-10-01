package com.aiexam.authservice.controller;

import com.aiexam.authservice.dto.ApiKeyResponse;
import com.aiexam.authservice.dto.SaveApiKeyRequest;
import com.aiexam.authservice.dto.UserResponse;
import com.aiexam.authservice.service.AiCredentialService;
import com.aiexam.authservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AiCredentialService aiCredentialService;

    @GetMapping("/api/v1/users/me")
    @Operation(summary = "Returns the authenticated user's profile and role")
    public ResponseEntity<UserResponse> getCurrentUser(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication));
    }

    @PostMapping("/api/v1/users/me/api-keys")
    @Operation(summary = "Saves or updates the caller's AI provider API key")
    public ResponseEntity<ApiKeyResponse> saveApiKey(
            @Valid @RequestBody SaveApiKeyRequest request, JwtAuthenticationToken authentication) {
        ApiKeyResponse response = aiCredentialService.saveApiKey(authentication.getToken(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/users/me/api-keys")
    @Operation(summary = "Lists the caller's saved AI provider API keys")
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(aiCredentialService.listApiKeys(authentication.getToken()));
    }
}
