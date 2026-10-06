package edu.ai.dyslexiaprisonbackend.service.speech;

import edu.ai.dyslexiaprisonbackend.dto.speech.SpeechAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class SpeechAnalysisClient {

    private final WebClient webClient;

    // Speech transcription is slower than gaze /analyze — give it real headroom.
    @Value("${ml.service.speech-timeout:20000}")
    private long timeoutMs;

    public SpeechAnalysisClient(
            WebClient.Builder webClientBuilder,
            @Value("${ml.service.url}") String mlServiceUrl
    ) {
        // NOTE: mlServiceUrl here is expected to be the FastAPI base URL
        // (e.g. http://localhost:8000), not the /analyze path — this client
        // appends /api/speech/analyze itself below.
        this.webClient = webClientBuilder.baseUrl(mlServiceUrl).build();
    }

    /**
     * FIX: was previously calling .block() inside a WebClient chain, tying
     * up the calling thread for the full transcription duration. Now returns
     * a CompletableFuture so the controller can respond asynchronously.
     */
    public CompletableFuture<SpeechAnalysisResponse> analyzeAsync(
            byte[] audio, String filename, String passage, String sessionId, String language) {

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        builder.part("reference_text", passage);
        if (sessionId != null) {
            builder.part("session_id", sessionId);
        }
        builder.part("language", language != null ? language : "en");

        return webClient.post()
                .uri("/api/speech/analyze")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(SpeechAnalysisResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(err -> log.error("✗ Speech analysis call failed for session {}: {}",
                        sessionId, err.getMessage()))
                .toFuture();
    }
}