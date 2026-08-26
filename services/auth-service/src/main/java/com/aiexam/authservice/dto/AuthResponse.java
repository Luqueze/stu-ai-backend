package com.aiexam.authservice.dto;

public record AuthResponse(String token, long expiresIn, UserResponse user) {}
