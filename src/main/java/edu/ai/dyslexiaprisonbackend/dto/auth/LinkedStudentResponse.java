package edu.ai.dyslexiaprisonbackend.dto.auth;

public record LinkedStudentResponse(
        Long studentId,
        String name,
        String message
) {
}