package edu.ai.dyslexiaprisonbackend.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String role,
        long expiresAt
) {}
