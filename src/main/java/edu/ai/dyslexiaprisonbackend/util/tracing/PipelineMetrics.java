package edu.ai.dyslexiaprisonbackend.util.tracing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PipelineMetrics - Tracks timing and flow for end-to-end validation
 * 
 * Records:
 * - Frame receive timestamp
 * - Buffer add timestamp
 * - Aggregation timestamp
 * - ML request send timestamp
 * - ML response receive timestamp
 * - Result routing timestamp
 * 
 * Provides latency breakdown:
 * - WebSocket → Buffer: network latency
 * - Buffer → Aggregation: buffering delay
 * - Aggregation → ML send: serialization
 * - ML send → ML response: ML inference
 * - Response → Routing: deserialization/routing
 */
@Slf4j
public class PipelineMetrics {
    
    @Data
    @Builder
    @AllArgsConstructor
    public static class PipelineEvent {
        private String correlationId;
        private String sessionId;
        private String stage; // "frame_receive", "buffer_add", "aggregation", "ml_send", "ml_response", "result_routing"
        private long timestamp; // System.currentTimeMillis()
        private Map<String, Object> metadata; // frame count, feature count, etc
        
        public String toLog() {
            return String.format(
                "[TRACE] %s | phase=%s | correlationId=%s | sessionId=%s | ts=%d | data=%s",
                stage.toUpperCase(), stage, correlationId, sessionId, timestamp, metadata
            );
        }
    }
    
    private static final Map<String, List<PipelineEvent>> eventLog = new ConcurrentHashMap<>();
    
    /**
     * Record a pipeline event
     */
    public static void recordEvent(String correlationId, String sessionId, String stage, Map<String, Object> metadata) {
        PipelineEvent event = PipelineEvent.builder()
                .correlationId(correlationId)
                .sessionId(sessionId)
                .stage(stage)
                .timestamp(System.currentTimeMillis())
                .metadata(metadata != null ? new HashMap<>(metadata) : Map.of())
                .build();
        
        eventLog.computeIfAbsent(correlationId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(event);
        
        log.debug(event.toLog());
    }
    
    /**
     * Get latency between two stages in milliseconds
     */
    public static long getLatency(String correlationId, String fromStage, String toStage) {
        List<PipelineEvent> events = eventLog.get(correlationId);
        if (events == null) return -1;
        
        long fromTime = events.stream()
                .filter(e -> e.stage.equals(fromStage))
                .mapToLong(e -> e.timestamp)
                .findFirst()
                .orElse(-1);
        
        long toTime = events.stream()
                .filter(e -> e.stage.equals(toStage))
                .mapToLong(e -> e.timestamp)
                .findFirst()
                .orElse(-1);
        
        return (fromTime > 0 && toTime > 0) ? (toTime - fromTime) : -1;
    }
    
    /**
     * Get complete pipeline timing breakdown
     */
    public static Map<String, Object> getCompleteMetrics(String correlationId) {
        List<PipelineEvent> events = eventLog.get(correlationId);
        if (events == null || events.isEmpty()) {
            return Map.of("error", "No events found for correlationId: " + correlationId);
        }
        
        long frameReceiveTime = getStageTime(events, "frame_receive");
        long bufferAddTime = getStageTime(events, "buffer_add");
        long aggregationTime = getStageTime(events, "aggregation");
        long mlSendTime = getStageTime(events, "ml_send");
        long mlResponseTime = getStageTime(events, "ml_response");
        long resultRoutingTime = getStageTime(events, "result_routing");
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("correlationId", correlationId);
        metrics.put("eventCount", events.size());
        
        if (frameReceiveTime > 0 && bufferAddTime > 0) {
            metrics.put("websocket_to_buffer_ms", bufferAddTime - frameReceiveTime);
        }
        if (bufferAddTime > 0 && aggregationTime > 0) {
            metrics.put("buffer_to_aggregation_ms", aggregationTime - bufferAddTime);
        }
        if (aggregationTime > 0 && mlSendTime > 0) {
            metrics.put("aggregation_to_ml_send_ms", mlSendTime - aggregationTime);
        }
        if (mlSendTime > 0 && mlResponseTime > 0) {
            metrics.put("ml_inference_ms", mlResponseTime - mlSendTime);
        }
        if (mlResponseTime > 0 && resultRoutingTime > 0) {
            metrics.put("ml_response_to_routing_ms", resultRoutingTime - mlResponseTime);
        }
        if (frameReceiveTime > 0 && resultRoutingTime > 0) {
            metrics.put("total_e2e_ms", resultRoutingTime - frameReceiveTime);
        }
        
        metrics.put("stages", events.stream()
                .map(e -> Map.of(
                        "stage", e.stage,
                        "timestamp", e.timestamp,
                        "relativeMs", e.timestamp - frameReceiveTime
                ))
                .toList());
        
        return metrics;
    }
    
    private static long getStageTime(List<PipelineEvent> events, String stage) {
        return events.stream()
                .filter(e -> e.stage.equals(stage))
                .map(e -> e.timestamp)
                .findFirst()
                .orElse(-1L);  // Use -1L to make it a long literal
    }
    
    /**
     * Clear metrics for a correlation ID (after session end or expiry)
     */
    public static void clear(String correlationId) {
        eventLog.remove(correlationId);
    }
    
    /**
     * Get all active correlation IDs being tracked
     */
    public static Set<String> getActiveCorrelationIds() {
        return new HashSet<>(eventLog.keySet());
    }
    
    /**
     * Print metrics to log (for debugging)
     */
    public static void logMetrics(String correlationId) {
        Map<String, Object> metrics = getCompleteMetrics(correlationId);
        log.info("[METRICS] Complete pipeline metrics: {}", metrics);
    }
}

