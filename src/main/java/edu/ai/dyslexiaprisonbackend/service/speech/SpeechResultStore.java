package edu.ai.dyslexiaprisonbackend.service.speech;

import edu.ai.dyslexiaprisonbackend.dto.speech.SpeechAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store so a speech result can be fetched later by sessionId,
 * same pattern as SessionResultStore for gaze results. Swap for a JPA table
 * when the dashboard persistence layer goes in — same public API.
 */
@Service
@Slf4j
public class SpeechResultStore {

    public record StoredSpeechResult(String username, SpeechAnalysisResponse result, long storedAt) {}

    private final Map<String, StoredSpeechResult> results = new ConcurrentHashMap<>();

    public void store(String sessionId, String username, SpeechAnalysisResponse result) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("⚠️ Refusing to store speech result with blank sessionId");
            return;
        }
        results.put(sessionId, new StoredSpeechResult(username, result, System.currentTimeMillis()));
        log.info("✓ Stored speech result: sessionId={}, combinedRiskScore={}",
                sessionId, result != null ? result.combinedRiskScore() : "null");
    }

    public Optional<StoredSpeechResult> get(String sessionId) {
        return Optional.ofNullable(results.get(sessionId));
    }
}