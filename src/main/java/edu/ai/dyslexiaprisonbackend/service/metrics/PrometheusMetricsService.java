package edu.ai.dyslexiaprisonbackend.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrometheusMetricsService - Collects application metrics for Prometheus
 *
 * Tracks:
 * - WebSocket connections (active, total)
 * - Frame metrics (received, dropped, rate)
 * - ML metrics (requests, responses, latency)
 * - Buffer metrics (active, flushed, overflow)
 *
 * Exposes via:
 * - GET /actuator/prometheus (Prometheus format)
 * - GET /actuator/metrics (JSON)
 * - GET /actuator/metrics/{metric.name}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PrometheusMetricsService {

    private final MeterRegistry meterRegistry;

    // ========================================================================
    // WebSocket Metrics
    // ========================================================================

    private final AtomicInteger activeWebSocketSessions = new AtomicInteger(0);
    private final AtomicLong totalWebSocketConnections = new AtomicLong(0);
    private final Map<String, Long> sessionStartTimes = new ConcurrentHashMap<>();

    public void recordWebSocketConnect(String sessionId) {
        activeWebSocketSessions.incrementAndGet();
        totalWebSocketConnections.incrementAndGet();
        sessionStartTimes.put(sessionId, System.currentTimeMillis());

        Counter.builder("websocket.connections.total")
                .description("Total WebSocket connections")
                .register(meterRegistry)
                .increment();

        log.debug("✓ WebSocket connection: {} (active: {})", sessionId, activeWebSocketSessions.get());
    }

    public void recordWebSocketDisconnect(String sessionId) {
        activeWebSocketSessions.decrementAndGet();

        Long startTime = sessionStartTimes.remove(sessionId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            Timer.builder("websocket.session.duration")
                    .description("WebSocket session duration")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry)
                    .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        log.debug("✓ WebSocket disconnection: {} (active: {})", sessionId, activeWebSocketSessions.get());
    }

    // ========================================================================
    // Frame Metrics
    // ========================================================================

    public void recordFrameReceived() {
        Counter.builder("gaze.frames.received.total")
                .description("Total gaze frames received")
                .register(meterRegistry)
                .increment();
    }

    public void recordFramesDropped(int count) {
        Counter.builder("gaze.frames.dropped.total")
                .description("Total gaze frames dropped")
                .register(meterRegistry)
                .increment(count);
    }

    public void recordFrameLatency(long latencyMs) {
        Timer.builder("gaze.frame.latency")
                .description("Gaze frame processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ========================================================================
    // ML Metrics
    // ========================================================================

    public void recordMlRequest(String modelName) {
        Counter.builder("ml.requests.total")
                .description("Total ML inference requests")
                .tag("model", modelName)
                .register(meterRegistry)
                .increment();
    }

    public void recordMlResponse(String modelName, long latencyMs, boolean success) {
        Timer.builder("ml.inference.latency")
                .description("ML model inference latency")
                .tag("model", modelName)
                .tag("status", success ? "success" : "failure")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        Counter.builder("ml.responses.total")
                .description("Total ML inference responses")
                .tag("model", modelName)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordMlTimeout(String modelName) {
        Counter.builder("ml.timeouts.total")
                .description("ML model inference timeouts")
                .tag("model", modelName)
                .register(meterRegistry)
                .increment();
    }

    public void recordMlFallback() {
        Counter.builder("ml.fallbacks.total")
                .description("Fallback predictions used")
                .register(meterRegistry)
                .increment();
    }

    public void recordRiskScore(Double score) {
        Gauge.builder("ml.risk.score", () -> score)
                .description("Current risk score")
                .register(meterRegistry);
    }

    // ========================================================================
    // Buffer Metrics
    // ========================================================================

    private final AtomicInteger activeBuffers = new AtomicInteger(0);

    public void recordBufferCreated() {
        activeBuffers.incrementAndGet();
    }

    public void recordBufferFlushed(int frameCount, int featureCount) {
        activeBuffers.decrementAndGet();

        Counter.builder("buffer.flushes.total")
                .description("Total buffer flushes")
                .register(meterRegistry)
                .increment();

        Gauge.builder("buffer.frames.per.flush", () -> frameCount)
                .description("Frames per buffer flush")
                .register(meterRegistry);
    }

    public void recordBufferOverflow(String sessionId) {
        Counter.builder("buffer.overflows.total")
                .description("Buffer overflow events")
                .tag("session", sessionId)
                .register(meterRegistry)
                .increment();
    }

    // ========================================================================
    // System Gauges (For Dashboard)
    // ========================================================================

    public void registerGauges() {
        // Active WebSocket sessions
        Gauge.builder("websocket.sessions.active", activeWebSocketSessions::get)
                .description("Active WebSocket sessions")
                .register(meterRegistry);

        // Active buffers
        Gauge.builder("buffer.active", activeBuffers::get)
                .description("Active session buffers")
                .register(meterRegistry);
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    public int getActiveWebSocketSessions() {
        return activeWebSocketSessions.get();
    }

    public long getTotalWebSocketConnections() {
        return totalWebSocketConnections.get();
    }

    public int getActiveBuffers() {
        return activeBuffers.get();
    }
}

