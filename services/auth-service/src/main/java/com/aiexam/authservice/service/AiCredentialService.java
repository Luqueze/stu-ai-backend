package com.aiexam.authservice.service;

import com.aiexam.authservice.dto.ApiKeyResponse;
import com.aiexam.authservice.dto.SaveApiKeyRequest;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.entity.UserAiCredential;
import com.aiexam.authservice.repository.UserAiCredentialRepository;
import com.aiexam.authservice.security.ApiKeyEncryptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiCredentialService {

    private final UserAiCredentialRepository credentialRepository;
    private final UserService userService;
    private final ApiKeyEncryptor apiKeyEncryptor;

    @Transactional
    public ApiKeyResponse saveApiKey(Jwt jwt, SaveApiKeyRequest request) {
        User user = userService.resolveCurrentUser(jwt);
        String encryptedApiKey = apiKeyEncryptor.encrypt(request.apiKey());

        UserAiCredential credential =
                credentialRepository
                        .findByUserIdAndProvider(user.getId(), request.provider())
                        .map(
                                existing ->
                                        UserAiCredential.builder()
                                                .id(existing.getId())
                                                .userId(user.getId())
                                                .provider(request.provider())
                                                .encryptedApiKey(encryptedApiKey)
                                                .createdAt(existing.getCreatedAt())
                                                .build())
                        .orElseGet(
                                () ->
                                        UserAiCredential.builder()
                                                .userId(user.getId())
                                                .provider(request.provider())
                                                .encryptedApiKey(encryptedApiKey)
                                                .build());

        return toResponse(credentialRepository.save(credential));
    }

    @Transactional
    public List<ApiKeyResponse> listApiKeys(Jwt jwt) {
        User user = userService.resolveCurrentUser(jwt);
        return credentialRepository.findByUserId(user.getId()).stream().map(this::toResponse).toList();
    }

    private ApiKeyResponse toResponse(UserAiCredential credential) {
        return new ApiKeyResponse(credential.getId(), credential.getProvider(), credential.getCreatedAt());
    }
}
