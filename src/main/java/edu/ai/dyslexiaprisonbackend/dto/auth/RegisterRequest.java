package edu.ai.dyslexiaprisonbackend.dto.auth;

import edu.ai.dyslexiaprisonbackend.model.user.RoleType;
import edu.ai.dyslexiaprisonbackend.util.validUtils.annotations.Password;
import jakarta.validation.constraints.*;
import lombok.NonNull;

import java.time.LocalDate;

public record RegisterRequest( @NotBlank
                               @Size(min = 3, max = 50)
                               String username,
                               @NotBlank
                               @Email
                               String email,
                               @NotBlank
                               @Size(min = 8, max = 72)
                               String password,
                               @NotNull
                               LocalDate dateOfBirth,
                               @NotBlank
                               @Pattern(regexp = "[MFO]")
                               String gender) {
}
