package edu.ai.dyslexiaprisonbackend.controller.monitor;

import edu.ai.dyslexiaprisonbackend.service.gaze.GazeDataService;
import edu.ai.dyslexiaprisonbackend.service.gaze.GazeSessionContext;
import edu.ai.dyslexiaprisonbackend.service.gaze.WebSocketActivityMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * WebSocket Monitoring API
 * 
 * Admin endpoints for monitoring WebSocket activity and debugging
 * 
 * Endpoints:
 * - GET /api/monitor/websocket/metrics - Global metrics
 * - GET /api/monitor/websocket/sessions - Active sessions
 * - GET /api/monitor/websocket/user/{username} - User metrics
 * 
 * Access: ADMIN role only
 */
@RestController
@RequestMapping("/api/monitor/websocket")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class WebSocketMonitorController {

    private final WebSocketActivityMonitor activityMonitor;
    private final GazeDataService gazeDataService;

    /**
     * Get global WebSocket metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getGlobalMetrics() {
        WebSocketActivityMonitor.GlobalMetrics metrics = activityMonitor.getGlobalMetrics();
        log.info("Admin requested global WebSocket metrics");
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get all active sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, ?>> getActiveSessions() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("userMetrics", activityMonitor.getAllUserMetrics());
        log.info("Admin requested active sessions overview");
        return ResponseEntity.ok(response);
    }

    /**
     * Get user-specific metrics
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<?> getUserMetrics(@PathVariable String username) {
        WebSocketActivityMonitor.UserMetrics metrics = activityMonitor.getUserMetrics(username);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("Admin requested metrics for user: {}", username);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get active sessions for a user
     */
    @GetMapping("/user/{username}/sessions")
    public ResponseEntity<List<Map<String, Object>>> getUserSessions(@PathVariable String username) {
        List<GazeSessionContext> sessions = gazeDataService.getUserActiveSessions(username);
        
        List<Map<String, Object>> sessionSummaries = sessions.stream()
                .map(session -> (Map<String, Object>) (Map<?,?>) Map.of(
                        "sessionId", session.getSessionId(),
                        "taskId", session.getTaskId(),
                        "durationMs", session.getDurationMs(),
                        "frameCount", session.getFrameCount(),
                        "featureCount", session.getFeatureCount(),
                        "avgConfidence", session.getAvgConfidence(),
                        "metadata", session.getMetadata()
                ))
                .toList();

        log.info("Admin requested sessions for user: {}", username);
        return ResponseEntity.ok(sessionSummaries);
    }

    /**
     * Log metrics (for debugging)
     */
    @PostMapping("/log-metrics")
    public ResponseEntity<String> logMetrics() {
        activityMonitor.logMetricsSummary();
        log.info("Admin triggered metrics logging");
        return ResponseEntity.ok("Metrics logged to stdout");
    }

    /**
     * Get system health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, ?>> getHealth() {
        WebSocketActivityMonitor.GlobalMetrics metrics = activityMonitor.getGlobalMetrics();
        
        boolean healthy = metrics.getActiveConnections() >= 0 
                && metrics.getAuthFailures() < 100;  // arbitrary threshold
        
        Map<String, Object> health = Map.of(
                "status", healthy ? "UP" : "DEGRADED",
                "activeConnections", metrics.getActiveConnections(),
                "authFailures", metrics.getAuthFailures(),
                "rateLimitHits", metrics.getRateLimitHits(),
                "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(health);
    }
}

