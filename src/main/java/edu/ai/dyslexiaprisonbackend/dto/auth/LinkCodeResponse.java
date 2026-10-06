package edu.ai.dyslexiaprisonbackend.dto.auth;

import edu.ai.dyslexiaprisonbackend.model.user.LinkType;

import java.time.LocalDateTime;

public record LinkCodeResponse(

        String code,

        LinkType type,

        LocalDateTime expiresAt

) {
}