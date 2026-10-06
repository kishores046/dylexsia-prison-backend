package edu.ai.dyslexiaprisonbackend.controller.speech;

import edu.ai.dyslexiaprisonbackend.dto.speech.SpeechAnalysisResponse;
import edu.ai.dyslexiaprisonbackend.service.speech.SpeechAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/speech")
@Slf4j
public class SpeechAnalysisController {

    private final SpeechAnalysisService speechAnalysisService;

    public SpeechAnalysisController(SpeechAnalysisService speechAnalysisService) {
        this.speechAnalysisService = speechAnalysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<?>> analyze(
            @RequestPart("file") MultipartFile file,
            @RequestPart("referenceText") String referenceText,
            @RequestPart(value = "sessionId", required = false) String sessionId,
            @RequestPart(value = "language", required = false) String language,
            Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Authentication required")));
        }

        if (file.isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }

        String username = auth.getName();
        String lang = (language == null || language.isBlank()) ? "en" : language;

        return speechAnalysisService
                .analyze(file, referenceText, sessionId, lang, username)
                .<ResponseEntity<?>>thenApply(ResponseEntity::ok)
                .exceptionally(err -> {
                    log.error("✗ Speech analysis endpoint error for {}: {}", username, err.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Speech analysis failed"));
                });
    }
}