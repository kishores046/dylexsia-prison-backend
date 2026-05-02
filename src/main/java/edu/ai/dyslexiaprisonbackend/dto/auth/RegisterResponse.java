package edu.ai.dyslexiaprisonbackend.dto.auth;

public record RegisterResponse(
        Long userId,
        String username,
        String email,
        String role,
        String message
) {}
