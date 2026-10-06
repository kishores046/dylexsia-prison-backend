package edu.ai.dyslexiaprisonbackend.controller;

import edu.ai.dyslexiaprisonbackend.service.ml.SessionResultStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Phase 1: REST endpoint for the frontend to pull a session's result after
 * gaze.session.end, instead of relying solely on the WebSocket push to
 * /user/queue/result. This exists because WS delivery has a real race
 * condition (client must be subscribed to /user/queue/result BEFORE the
 * result is routed, or it's silently missed) — REST polling has no such
 * timing dependency.
 */
@RestController
@RequestMapping("/api/gaze")
@Slf4j
@RequiredArgsConstructor
public class GazeResultController {

    private final SessionResultStore resultStore;

    @GetMapping("/sessions/{sessionId}/result")
    public ResponseEntity<?> getSessionResult(
            @PathVariable String sessionId,
            Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        log.info("🔥 GazeResultController HIT: {}", sessionId);
        String username = auth.getName();

        Optional<SessionResultStore.StoredResult> stored = resultStore.get(sessionId);

        if (stored.isEmpty()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "status", "PENDING",
                            "sessionId", sessionId,
                            "message", "Result not yet available"
                    ));
        }

        SessionResultStore.StoredResult sr = stored.get();


        if (!sr.username().equals(username)) {
            log.warn("⚠️ User {} attempted to fetch session {} owned by {}",
                    username, sessionId, sr.username());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not authorized to view this session"));
        }

        return ResponseEntity.ok(sr.result());
    }
}