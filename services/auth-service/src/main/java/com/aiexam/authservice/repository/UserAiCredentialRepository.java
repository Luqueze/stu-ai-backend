package com.aiexam.authservice.repository;

import com.aiexam.authservice.entity.UserAiCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAiCredentialRepository extends JpaRepository<UserAiCredential, UUID> {

    List<UserAiCredential> findByUserId(UUID userId);

    Optional<UserAiCredential> findByUserIdAndProvider(UUID userId, String provider);
}
