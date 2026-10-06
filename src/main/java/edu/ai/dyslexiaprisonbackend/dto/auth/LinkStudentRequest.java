package edu.ai.dyslexiaprisonbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LinkStudentRequest(

        @NotBlank
        String code

) {
}