package edu.ai.dyslexiaprisonbackend.dto.auth;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresAt
) {}
