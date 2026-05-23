package edu.ai.dyslexiaprisonbackend.service.fallback;

import edu.ai.dyslexiaprisonbackend.dto.ml.MlResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FallbackModeService - Provides operational fallback modes
 * 
 * Modes:
 * 1. MOCK - Deterministic fake ML responses (for testing)
 * 2. OFFLINE - Queue requests, replay later (for network outages)
 * 3. REPLAY - Return historical session data (for debugging)
 * 4. DEGRADED - Use cached results (for ML service outages)
 * 
 * Enabled via:
 * @FallbackMode(mode = Mode.MOCK)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FallbackModeService {
    
    public enum Mode {
        PRODUCTION,   // Normal ML service calls
        MOCK,         // Deterministic fake responses
        OFFLINE,      // Queue and replay
        REPLAY,       // Historical data
        DEGRADED      // Cached results
    }
    
    private volatile Mode currentMode = Mode.PRODUCTION;
    private final Queue<OfflineRequest> offlineQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, MlResultDto> sessionResultCache = new ConcurrentHashMap<>();
    private final Random random = new Random(12345); // Fixed seed for deterministic mock results
    
    // ========================================================================
    // MODE MANAGEMENT
    // ========================================================================
    
    public void setMode(Mode mode) {
        log.warn("⚠️ Fallback mode changed: {} → {}", currentMode, mode);
        this.currentMode = mode;
    }
    
    public Mode getMode() {
        return currentMode;
    }
    
    public boolean isMockMode() {
        return currentMode == Mode.MOCK;
    }
    
    public boolean isOfflineMode() {
        return currentMode == Mode.OFFLINE;
    }
    
    public boolean isReplayMode() {
        return currentMode == Mode.REPLAY;
    }
    
    public boolean isDegradedMode() {
        return currentMode == Mode.DEGRADED;
    }
    
    // ========================================================================
    // MOCK MODE - Deterministic Fake Responses
    // ========================================================================
    
    /**
     * Generate mock ML result (deterministic based on frame count)
     */
    public MlResultDto generateMockResult(String sessionId, int frameCount, Map<String, Double> features) {
        // Use frame count to generate deterministic but varied responses
        double seed = (double) frameCount / 100.0; // 0.0-1.0+ range
        
        double riskScore = 0.3 + (seed % 0.4);  // 0.3-0.7 range
        riskScore = Math.min(1.0, riskScore);
        
        String classification = classifyRisk(riskScore);
        double confidence = 0.75 + (random.nextDouble() * 0.2); // 0.75-0.95
        double ruleScore = riskScore + (random.nextDouble() * 0.1 - 0.05);
        double rfScore = riskScore - (random.nextDouble() * 0.1 - 0.05);
        
        // Normalize scores to 0-1
        ruleScore = Math.max(0, Math.min(1, ruleScore));
        rfScore = Math.max(0, Math.min(1, rfScore));
        confidence = Math.max(0, Math.min(1, confidence));
        
        Map<String, Double> breakdown = Map.of(
                "ruleScore", ruleScore,
                "rfScore", rfScore
        );
        
        long processingTimeMs = 800 + random.nextInt(400); // 800-1200ms
        
        MlResultDto result = MlResultDto.builder()
                .sessionId(sessionId)
                .riskScore(riskScore)
                .classification(classification)
                .confidence(confidence)
                .breakdown(breakdown)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of("processingTimeMs", processingTimeMs))
                .build();
        
        log.info("🤖 [MOCK] Generated result: sessionId={}, riskScore={:.2f}, classification={}", 
                sessionId, riskScore, classification);
        
        return result;
    }
    
    private String classifyRisk(double riskScore) {
        if (riskScore < 0.3) return "no_risk";
        if (riskScore < 0.5) return "mild_risk";
        if (riskScore < 0.7) return "moderate_risk";
        return "high_risk";
    }
    
    // ========================================================================
    // OFFLINE MODE - Queue and Replay
    // ========================================================================
    
    /**
     * Queue request for later processing
     */
    public void queueOfflineRequest(String sessionId, String username, Map<String, Double> features) {
        OfflineRequest request = new OfflineRequest(sessionId, username, features, System.currentTimeMillis());
        offlineQueue.add(request);
        log.warn("📦 [OFFLINE] Queued request: sessionId={}, queueSize={}", sessionId, offlineQueue.size());
    }
    
    /**
     * Get queued requests
     */
    public Queue<OfflineRequest> getQueuedRequests() {
        return new LinkedList<>(offlineQueue);
    }
    
    /**
     * Clear offline queue
     */
    public void clearOfflineQueue() {
        int size = offlineQueue.size();
        offlineQueue.clear();
        log.info("✓ Offline queue cleared: {} requests discarded", size);
    }
    
    /**
     * Replay offline requests (to be called when service recovers)
     */
    public int replayQueuedRequests() {
        int count = 0;
        while (!offlineQueue.isEmpty()) {
            OfflineRequest request = offlineQueue.poll();
            // TODO: Call ML service with request
            log.info("🔄 [REPLAY] Replayed request: {}", request.sessionId);
            count++;
        }
        log.warn("🔄 [REPLAY] Completed: {} requests replayed", count);
        return count;
    }
    
    // ========================================================================
    // REPLAY MODE - Historical Session Data
    // ========================================================================
    
    /**
     * Cache ML result for session (for replay mode)
     */
    public void cacheSessionResult(String sessionId, MlResultDto result) {
        sessionResultCache.put(sessionId, result);
    }
    
    /**
     * Get cached result for session (or null if not available)
     */
    public MlResultDto getCachedResult(String sessionId) {
        return sessionResultCache.get(sessionId);
    }
    
    /**
     * Clear result cache
     */
    public void clearResultCache() {
        int size = sessionResultCache.size();
        sessionResultCache.clear();
        log.info("✓ Result cache cleared: {} results removed", size);
    }
    
    // ========================================================================
    // STATUS
    // ========================================================================
    
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", currentMode.toString());
        status.put("offlineQueueSize", offlineQueue.size());
        status.put("cachedResultsCount", sessionResultCache.size());
        status.put("description", getModeDescription());
        return status;
    }
    
    private String getModeDescription() {
        return switch (currentMode) {
            case PRODUCTION -> "✅ Normal operation - ML service active";
            case MOCK -> "🤖 Mock mode - Generating deterministic responses";
            case OFFLINE -> "📦 Offline mode - Requests queued for replay";
            case REPLAY -> "🔄 Replay mode - Returning historical data";
            case DEGRADED -> "⚠️ Degraded mode - Using cached results";
        };
    }
    
    // ========================================================================
    // OfflineRequest Data Class
    // ========================================================================
    
    public static class OfflineRequest {
        public String sessionId;
        public String username;
        public Map<String, Double> features;
        public long queuedAt;
        
        public OfflineRequest(String sessionId, String username, Map<String, Double> features, long queuedAt) {
            this.sessionId = sessionId;
            this.username = username;
            this.features = new HashMap<>(features);
            this.queuedAt = queuedAt;
        }
        
        public long getAgeMs() {
            return System.currentTimeMillis() - queuedAt;
        }
    }
}

