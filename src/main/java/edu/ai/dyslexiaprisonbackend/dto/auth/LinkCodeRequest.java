package edu.ai.dyslexiaprisonbackend.dto.auth;

import edu.ai.dyslexiaprisonbackend.model.user.LinkType;
import jakarta.validation.constraints.NotNull;

public record LinkCodeRequest(

        @NotNull
        LinkType type

) {
}