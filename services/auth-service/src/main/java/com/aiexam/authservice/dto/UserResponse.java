package com.aiexam.authservice.dto;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role) {}
