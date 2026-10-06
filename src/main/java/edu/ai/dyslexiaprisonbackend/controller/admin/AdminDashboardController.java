package edu.ai.dyslexiaprisonbackend.controller.admin;

import edu.ai.dyslexiaprisonbackend.repository.*;
import edu.ai.dyslexiaprisonbackend.service.fallback.FallbackModeService;
import edu.ai.dyslexiaprisonbackend.service.metrics.PrometheusMetricsService;
import edu.ai.dyslexiaprisonbackend.service.buffer.SessionBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AdminDashboardController - Operational monitoring and debug dashboard
 * 
 * Endpoints:
 * - GET /api/admin/dashboard - Overall system status
 * - GET /api/admin/metrics - Real-time metrics
 * - GET /api/admin/sessions - Active sessions
 * - POST /api/admin/fallback/mode - Set fallback mode
 * - GET /api/admin/fallback/mode - Get fallback mode
 * 
 * Requires: ?token=secret or Authentication header
 * 
 * Disabled in production by default (enable with admin.dashboard.enabled=true)
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.dashboard.enabled", havingValue = "true", matchIfMissing = false)
public class AdminDashboardController {
    
    private final GazeSessionRepository gazeSessionRepository;
    private final MlResultRepository mlResultRepository;
    private final SessionMetricsRepository metricsRepository;
    private final AuditEventRepository auditEventRepository;
    private final PrometheusMetricsService metricsService;
    private final FallbackModeService fallbackModeService;
    private final SessionBufferService bufferService;
    

    
    /**
     * GET /api/admin/dashboard
     * Returns overall system status dashboard data
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        Map<String, Object> dashboard = new LinkedHashMap<>();
        
        // System health
        dashboard.put("timestamp", LocalDateTime.now());
        dashboard.put("status", "operational");
        
        // Real-time metrics
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("activeWebSocketSessions", metricsService.getActiveWebSocketSessions());
        metrics.put("totalConnections", metricsService.getTotalWebSocketConnections());
        metrics.put("activeSessions", gazeSessionRepository.findBySessionStatus("active").size());
        metrics.put("activeBuffers", metricsService.getActiveBuffers());
        metrics.put("fallbackMode", fallbackModeService.getMode().toString());
        dashboard.put("realtime", metrics);
        
        // Today's statistics
        LocalDateTime today = LocalDateTime.now().minusHours(24);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sessionsToday", gazeSessionRepository.findUserSessionsSince("*", today).size());
        stats.put("resultsToday", mlResultRepository.findResultsSince(today).size());
        stats.put("averageConfidence", mlResultRepository.getAverageConfidence());
        stats.put("averageLatency", metricsRepository.getAverageLatency());
        dashboard.put("dailyStats", stats);
        
        // Recent errors
        dashboard.put("recentErrors", auditEventRepository.findRecentErrors(today)
                .stream()
                .limit(10)
                .map(e -> Map.of(
                        "eventType", e.getEventType(),
                        "message", e.getEventMessage(),
                        "timestamp", e.getTimestamp()
                ))
                .collect(Collectors.toList()));
        
        // Fallback mode status
        dashboard.put("fallbackMode", fallbackModeService.getStatus());
        
        return ResponseEntity.ok(dashboard);
    }
    

    /**
     * GET /api/admin/metrics
     * Returns detailed metrics for Grafana or custom dashboards
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(@RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", LocalDateTime.now());
        
        // WebSocket metrics
        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("activeSessions", metricsService.getActiveWebSocketSessions());
        websocket.put("totalConnections", metricsService.getTotalWebSocketConnections());
        metrics.put("websocket", websocket);
        
        // Frame metrics
        Map<String, Object> frames = new LinkedHashMap<>();
        frames.put("activeBuffers", metricsService.getActiveBuffers());
        metrics.put("frames", frames);
        
        // ML metrics
        Map<String, Object> ml = new LinkedHashMap<>();
        ml.put("averageConfidence", mlResultRepository.getAverageConfidence());
        ml.put("averageInferenceTime", metricsRepository.getAverageLatency());
        metrics.put("ml", ml);
        
        // Database metrics
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("totalSessions", gazeSessionRepository.count());
        database.put("totalResults", mlResultRepository.count());
        database.put("totalMetrics", metricsRepository.count());
        database.put("totalAuditEvents", auditEventRepository.count());
        metrics.put("database", database);
        
        return ResponseEntity.ok(metrics);
    }
    
    // ========================================================================
    // SESSIONS
    // ========================================================================
    
    /**
     * GET /api/admin/sessions
     * Returns active and recent sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
            @RequestParam(required = false) String token,
            @RequestParam(defaultValue = "10") int limit) {
        verifyAdminToken(token);
        
        Map<String, Object> response = new LinkedHashMap<>();
        
        // Active sessions
        var activeSessions = gazeSessionRepository.findBySessionStatus("active")
                .stream()
                .limit(limit)
                .map(s -> Map.of(
                        "sessionId", s.getSessionId(),
                        "username", s.getUsername(),
                        "taskId", s.getTaskId(),
                        "startedAt", s.getStartedAt(),
                        "frameCount", s.getFrameCount(),
                        "featureCount", s.getFeatureCount()
                ))
                .collect(Collectors.toList());
        
        response.put("activeSessions", activeSessions);
        response.put("activeSessCount", activeSessions.size());
        
        return ResponseEntity.ok(response);
    }
    
    // ========================================================================
    // FALLBACK MODE MANAGEMENT
    // ========================================================================
    
    /**
     * POST /api/admin/fallback/mode
     * Set fallback mode (PRODUCTION, MOCK, OFFLINE, REPLAY, DEGRADED)
     */
    @PostMapping("/fallback/mode")
    public ResponseEntity<Map<String, Object>> setFallbackMode(
            @RequestParam String mode,
            @RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        try {
            FallbackModeService.Mode newMode = FallbackModeService.Mode.valueOf(mode.toUpperCase());
            fallbackModeService.setMode(newMode);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("mode", newMode.toString());
            response.put("message", "Fallback mode changed to: " + newMode);
            response.put("status", fallbackModeService.getStatus());
            
            log.warn("🔧 [ADMIN] Fallback mode set to: {}", newMode);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid mode: " + mode,
                    "validModes", Arrays.stream(FallbackModeService.Mode.values())
                            .map(Enum::toString)
                            .collect(Collectors.toList())
            ));
        }
    }
    
    /**
     * GET /api/admin/fallback/mode
     * Get current fallback mode
     */
    @GetMapping("/fallback/mode")
    public ResponseEntity<Map<String, Object>> getFallbackMode(
            @RequestParam(required = false) String token) {
        verifyAdminToken(token);
        return ResponseEntity.ok(fallbackModeService.getStatus());
    }
    
    /**
     * GET /api/admin/fallback/queue
     * Get offline queue status
     */
    @GetMapping("/fallback/queue")
    public ResponseEntity<Map<String, Object>> getOfflineQueue(
            @RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        Map<String, Object> response = new LinkedHashMap<>();
        var queue = fallbackModeService.getQueuedRequests();
        response.put("queueSize", queue.size());
        response.put("requests", queue.stream()
                .map(r -> Map.of(
                        "sessionId", r.sessionId,
                        "username", r.username,
                        "ageMs", r.getAgeMs()
                ))
                .collect(Collectors.toList()));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * POST /api/admin/fallback/queue/replay
     * Replay queued requests
     */
    @PostMapping("/fallback/queue/replay")
    public ResponseEntity<Map<String, Object>> replayQueue(
            @RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        int replayed = fallbackModeService.replayQueuedRequests();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("replayed", replayed);
        response.put("message", "Replayed " + replayed + " queued requests");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * DELETE /api/admin/fallback/queue
     * Clear offline queue
     */
    @DeleteMapping("/fallback/queue")
    public ResponseEntity<Map<String, Object>> clearQueue(
            @RequestParam(required = false) String token) {
        verifyAdminToken(token);
        
        fallbackModeService.clearOfflineQueue();
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Offline queue cleared"
        ));
    }
    
    // ========================================================================
    // HEALTH CHECK
    // ========================================================================
    
    /**
     * GET /api/admin/health
     * Simple health check (no auth required for monitoring systems)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
    
    // ========================================================================
    // Authorization
    // ========================================================================
    
    private void verifyAdminToken(String token) {
        // Simple token verification - replace with proper auth
        String expectedToken = System.getenv("ADMIN_TOKEN");
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (token == null || !token.equals(expectedToken)) {
                throw new IllegalArgumentException("Invalid or missing admin token");
            }
        }
    }
}

