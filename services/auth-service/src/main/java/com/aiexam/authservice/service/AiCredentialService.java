package com.aiexam.authservice.service;

import com.aiexam.authservice.dto.ApiKeyResponse;
import com.aiexam.authservice.dto.SaveApiKeyRequest;
import com.aiexam.authservice.entity.User;
import com.aiexam.authservice.entity.UserAiCredential;
import com.aiexam.authservice.repository.UserAiCredentialRepository;
import com.aiexam.authservice.repository.UserRepository;
import com.aiexam.authservice.security.ApiKeyEncryptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCredentialService {

    private final UserAiCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;

    public ApiKeyResponse saveApiKey(String email, SaveApiKeyRequest request) {
        User user = requireUser(email);
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

    public List<ApiKeyResponse> listApiKeys(String email) {
        User user = requireUser(email);
        return credentialRepository.findByUserId(user.getId()).stream().map(this::toResponse).toList();
    }

    private User requireUser(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid session"));
    }

    private ApiKeyResponse toResponse(UserAiCredential credential) {
        return new ApiKeyResponse(credential.getId(), credential.getProvider(), credential.getCreatedAt());
    }
}
