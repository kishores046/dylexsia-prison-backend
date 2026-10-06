package edu.ai.dyslexiaprisonbackend.service.speech;

import edu.ai.dyslexiaprisonbackend.dto.speech.SpeechAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SpeechAnalysisService {

    private final SpeechAnalysisClient speechAnalysisClient;
    private final SpeechResultStore speechResultStore;

    public SpeechAnalysisService(
            SpeechAnalysisClient speechAnalysisClient,
            SpeechResultStore speechResultStore
    ) {
        this.speechAnalysisClient = speechAnalysisClient;
        this.speechResultStore = speechResultStore;
    }

    public CompletableFuture<SpeechAnalysisResponse> analyze(
            MultipartFile audio, String passage, String sessionId, String language, String username) {

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            CompletableFuture<SpeechAnalysisResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("Failed to read uploaded audio", e));
            return failed;
        }

        return speechAnalysisClient
                .analyzeAsync(bytes, audio.getOriginalFilename(), passage, sessionId, language)
                .thenApply(result -> {
                    if (sessionId != null && result != null) {
                        speechResultStore.store(sessionId, username, result);
                    }
                    return result;
                })
                .exceptionally(err -> {
                    log.error("✗ Speech analysis failed for user {}: {}", username, err.getMessage(), err);
                    throw new RuntimeException("Speech analysis failed", err);
                });
    }
}