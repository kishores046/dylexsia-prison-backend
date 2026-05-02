package edu.ai.dyslexiaprisonbackend.service.gaze;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket Activity Monitor
 * 
 * Tracks real-time metrics for:
 * - Active connections
 * - Messages processed
 * - Errors and rate limits
 * - Per-user statistics
 * 
 * Used for:
 * - Production monitoring
 * - Performance debugging
 * - Analytics dashboards
 * 
 * Thread-safe using atomic types and concurrent collections.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketActivityMonitor {

    // Global metrics
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalFrameCount = new AtomicLong(0);
    private final AtomicLong totalFeatureCount = new AtomicLong(0);
    private final AtomicLong rateLimitHits = new AtomicLong(0);
    private final AtomicLong authFailures = new AtomicLong(0);
    private final AtomicLong validationErrors = new AtomicLong(0);

    // Per-user metrics
    private final ConcurrentHashMap<String, UserMetrics> userMetrics = new ConcurrentHashMap<>();

    /**
     * Record WebSocket connection
     */
    public void recordConnection(String username) {
        activeConnections.incrementAndGet();
        userMetrics.computeIfAbsent(username, k -> new UserMetrics(username))
                .recordConnection();
        log.debug("WebSocket connected: {} (total active: {})", username, activeConnections.get());
    }

    /**
     * Record WebSocket disconnection
     */
    public void recordDisconnection(String username) {
        activeConnections.decrementAndGet();
        UserMetrics metrics = userMetrics.get(username);
        if (metrics != null) {
            metrics.recordDisconnection();
        }
        log.debug("WebSocket disconnected: {} (total active: {})", username, activeConnections.get());
    }

    /**
     * Record successful frame processing
     */
    public void recordFrameProcessed(String username) {
        totalFrameCount.incrementAndGet();
        UserMetrics metrics = userMetrics.computeIfAbsent(username, k -> new UserMetrics(username));
        metrics.recordFrame();
    }

    /**
     * Record successful feature processing
     */
    public void recordFeatureProcessed(String username) {
        totalFeatureCount.incrementAndGet();
        UserMetrics metrics = userMetrics.computeIfAbsent(username, k -> new UserMetrics(username));
        metrics.recordFeature();
    }

    /**
     * Record rate limit hit
     */
    public void recordRateLimitHit(String username, String type) {
        rateLimitHits.incrementAndGet();
        UserMetrics metrics = userMetrics.get(username);
        if (metrics != null) {
            metrics.recordRateLimitHit();
        }
        log.warn("Rate limit exceeded for {}: {}", username, type);
    }

    /**
     * Record authentication failure
     */
    public void recordAuthFailure(String reason) {
        authFailures.incrementAndGet();
        log.warn("Authentication failure: {}", reason);
    }

    /**
     * Record validation error
     */
    public void recordValidationError(String username, String entityType) {
        validationErrors.incrementAndGet();
        UserMetrics metrics = userMetrics.get(username);
        if (metrics != null) {
            metrics.recordValidationError();
        }
        log.warn("Validation error for {}: {}", username, entityType);
    }

    /**
     * Get global metrics snapshot
     */
    public GlobalMetrics getGlobalMetrics() {
        return GlobalMetrics.builder()
                .timestamp(LocalDateTime.now())
                .activeConnections(activeConnections.get())
                .totalFrameCount(totalFrameCount.get())
                .totalFeatureCount(totalFeatureCount.get())
                .rateLimitHits(rateLimitHits.get())
                .authFailures(authFailures.get())
                .validationErrors(validationErrors.get())
                .build();
    }

    /**
     * Get per-user metrics
     */
    public UserMetrics getUserMetrics(String username) {
        return userMetrics.get(username);
    }

    /**
     * Get all user metrics
     */
    public Map<String, UserMetrics> getAllUserMetrics() {
        return new ConcurrentHashMap<>(userMetrics);
    }

    /**
     * Get metrics summary (for logging/debugging)
     */
    public void logMetricsSummary() {
        GlobalMetrics global = getGlobalMetrics();
        log.info("=== WebSocket Metrics Summary ===");
        log.info("Active connections: {}", global.getActiveConnections());
        log.info("Total frames: {}", global.getTotalFrameCount());
        log.info("Total features: {}", global.getTotalFeatureCount());
        log.info("Rate limit hits: {}", global.getRateLimitHits());
        log.info("Auth failures: {}", global.getAuthFailures());
        log.info("Validation errors: {}", global.getValidationErrors());
        log.info("=====================================");
    }

    @Data
    public static class GlobalMetrics {
        private LocalDateTime timestamp;
        private Long activeConnections;
        private Long totalFrameCount;
        private Long totalFeatureCount;
        private Long rateLimitHits;
        private Long authFailures;
        private Long validationErrors;

        public GlobalMetrics(LocalDateTime timestamp, Long activeConnections, 
                           Long totalFrameCount, Long totalFeatureCount,
                           Long rateLimitHits, Long authFailures, Long validationErrors) {
            this.timestamp = timestamp;
            this.activeConnections = activeConnections;
            this.totalFrameCount = totalFrameCount;
            this.totalFeatureCount = totalFeatureCount;
            this.rateLimitHits = rateLimitHits;
            this.authFailures = authFailures;
            this.validationErrors = validationErrors;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private LocalDateTime timestamp;
            private Long activeConnections;
            private Long totalFrameCount;
            private Long totalFeatureCount;
            private Long rateLimitHits;
            private Long authFailures;
            private Long validationErrors;

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder activeConnections(Long activeConnections) {
                this.activeConnections = activeConnections;
                return this;
            }

            public Builder totalFrameCount(Long totalFrameCount) {
                this.totalFrameCount = totalFrameCount;
                return this;
            }

            public Builder totalFeatureCount(Long totalFeatureCount) {
                this.totalFeatureCount = totalFeatureCount;
                return this;
            }

            public Builder rateLimitHits(Long rateLimitHits) {
                this.rateLimitHits = rateLimitHits;
                return this;
            }

            public Builder authFailures(Long authFailures) {
                this.authFailures = authFailures;
                return this;
            }

            public Builder validationErrors(Long validationErrors) {
                this.validationErrors = validationErrors;
                return this;
            }

            public GlobalMetrics build() {
                return new GlobalMetrics(timestamp, activeConnections, totalFrameCount,
                        totalFeatureCount, rateLimitHits, authFailures, validationErrors);
            }
        }
    }

    /**
     * Per-user metrics
     */
    @Data
    public static class UserMetrics {
        private String username;
        private Long connectionCount = 0L;
        private Long frameCount = 0L;
        private Long featureCount = 0L;
        private Long rateLimitHits = 0L;
        private Long validationErrors = 0L;
        private LocalDateTime firstConnected;
        private LocalDateTime lastConnected;

        public UserMetrics(String username) {
            this.username = username;
        }

        public synchronized void recordConnection() {
            this.connectionCount++;
            this.lastConnected = LocalDateTime.now();
            if (this.firstConnected == null) {
                this.firstConnected = this.lastConnected;
            }
        }

        public synchronized void recordDisconnection() {
            this.lastConnected = LocalDateTime.now();
        }

        public synchronized void recordFrame() {
            this.frameCount++;
        }

        public synchronized void recordFeature() {
            this.featureCount++;
        }

        public synchronized void recordRateLimitHit() {
            this.rateLimitHits++;
        }

        public synchronized void recordValidationError() {
            this.validationErrors++;
        }

        public Double getFramesPerSecond() {
            if (connectionCount == 0 || frameCount == 0) return 0.0;
            long connectionDurationSecs = java.time.temporal.ChronoUnit.SECONDS.between(
                    firstConnected, lastConnected.plusSeconds(1));
            return connectionDurationSecs > 0 ? (double) frameCount / connectionDurationSecs : 0.0;
        }
    }
}

