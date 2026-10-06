package edu.ai.dyslexiaprisonbackend.service.ml;

import edu.ai.dyslexiaprisonbackend.dto.ml.MlResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1: in-memory store so a completed ML result can be fetched via REST,
 * not just pushed once over WebSocket. This is intentionally NOT a database —
 * it's a short-lived cache of "last known result per session" so the frontend
 * has a reliable pull-based fallback to the WS push. Swap for a JPA-backed
 * SessionResult table later (already planned in the dashboard work) without
 * changing this class's public API.
 */
@Service
@Slf4j
public class SessionResultStore {

    public record StoredResult(String username, MlResultDto result, long storedAt) {}

    private final Map<String, StoredResult> results = new ConcurrentHashMap<>();

    public void store(String sessionId, String username, MlResultDto result) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("⚠️ Refusing to store result with blank sessionId");
            return;
        }
        results.put(sessionId, new StoredResult(username, result, System.currentTimeMillis()));
        log.info("✓ Stored result for later REST fetch: sessionId={}, classification={}",
                sessionId, result != null ? result.getClassification() : "null");
    }

    public Optional<StoredResult> get(String sessionId) {
        return Optional.ofNullable(results.get(sessionId));
    }

    /** Call on session end / cleanup if you want to bound memory growth. */
    public void remove(String sessionId) {
        results.remove(sessionId);
    }
}
