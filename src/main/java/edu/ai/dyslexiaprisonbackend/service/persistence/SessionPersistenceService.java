package edu.ai.dyslexiaprisonbackend.service.persistence;

import edu.ai.dyslexiaprisonbackend.model.session.GazeSession;
import edu.ai.dyslexiaprisonbackend.model.result.MlResult;
import edu.ai.dyslexiaprisonbackend.model.metrics.SessionMetrics;
import edu.ai.dyslexiaprisonbackend.model.audit.AuditEvent;
import edu.ai.dyslexiaprisonbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SessionPersistenceService - Persists session data to database
 * 
 * Responsibilities:
 * - Save session lifecycle (start, update, end)
 * - Persist ML results
 * - Record performance metrics
 * - Maintain audit trail
 * 
 * TRANSACTIONAL: All operations are atomic
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SessionPersistenceService {
    
    private final GazeSessionRepository gazeSessionRepository;
    private final MlResultRepository mlResultRepository;
    private final SessionMetricsRepository metricsRepository;
    private final AuditEventRepository auditEventRepository;
    
    // ========================================================================
    // SESSION LIFECYCLE
    // ========================================================================
    
    /**
     * Create new session record
     */
    public GazeSession createSession(String sessionId, String username, String taskId, Map<String, Object> deviceMetadata) {
        // Serialize device metadata to JSON string
        String deviceJson = "{}";
        if (deviceMetadata != null && !deviceMetadata.isEmpty()) {
            try {
                // Simple serialization: convert map to JSON string
                StringBuilder sb = new StringBuilder("{");
                deviceMetadata.forEach((key, value) -> 
                    sb.append("\"").append(key).append("\":\"").append(value).append("\",")
                );
                if (sb.length() > 1) {
                    sb.setLength(sb.length() - 1); // Remove trailing comma
                }
                sb.append("}");
                deviceJson = sb.toString();
            } catch (Exception e) {
                log.warn("Failed to serialize device metadata, using default", e);
                deviceJson = "{}";
            }
        }
        
        GazeSession session = GazeSession.builder()
                .sessionId(sessionId)
                .username(username)
                .taskId(taskId)
                .startedAt(LocalDateTime.now())
                .sessionStatus("active")
                .deviceMetadata(deviceJson)
                .frameCount(0)
                .featureCount(0)
                .build();
        
        GazeSession saved = gazeSessionRepository.save(session);
        log.info("✓ Session created: {} for user: {}", sessionId, username);
        return saved;
    }
    
    /**
     * Update session statistics
     */
    public void updateSessionStats(String sessionId, Integer frameCount, Integer featureCount, Double averageLatency) {
        gazeSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setFrameCount(frameCount != null ? frameCount : session.getFrameCount());
            session.setFeatureCount(featureCount != null ? featureCount : session.getFeatureCount());
            session.setAverageLatencyMs(averageLatency != null ? averageLatency : session.getAverageLatencyMs());
            gazeSessionRepository.save(session);
            log.debug("✓ Session stats updated: {}", sessionId);
        });
    }
    
    /**
     * Complete session
     */
    public void endSession(String sessionId) {
        gazeSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setEndedAt(LocalDateTime.now());
            session.setSessionStatus("completed");
            gazeSessionRepository.save(session);
            log.info("✓ Session ended: {}", sessionId);
        });
    }
    
    // ========================================================================
    // ML RESULTS
    // ========================================================================
    
    /**
     * Save ML analysis result
     */
    public MlResult saveMlResult(String sessionId, Double riskScore, String classification, 
                                 Double confidence, Double ruleScore, Double rfScore, Long processingTimeMs) {
        MlResult result = MlResult.builder()
                .sessionId(sessionId)
                .riskScore(riskScore)
                .classification(classification)
                .confidence(confidence)
                .ruleScore(ruleScore)
                .rfScore(rfScore)
                .processingTimeMs(processingTimeMs)
                .build();
        
        MlResult saved = mlResultRepository.save(result);
        log.info("✓ ML result saved: session={}, classification={}, confidence={:.2f}", 
                sessionId, classification, confidence);
        return saved;
    }
    
    // ========================================================================
    // METRICS
    // ========================================================================
    
    /**
     * Create or update session metrics
     */
    public SessionMetrics saveSessionMetrics(String sessionId, Integer frameCount, Integer featureCount,
                                             Integer droppedFrames, Double averageLatency, 
                                             Double maxLatency, Double minLatency, Integer disconnects) {
        SessionMetrics metrics = SessionMetrics.builder()
                .sessionId(sessionId)
                .frameCount(frameCount != null ? frameCount : 0)
                .featureCount(featureCount != null ? featureCount : 0)
                .droppedFrames(droppedFrames != null ? droppedFrames : 0)
                .averageLatencyMs(averageLatency)
                .maxLatencyMs(maxLatency)
                .minLatencyMs(minLatency)
                .websocketDisconnects(disconnects != null ? disconnects : 0)
                .build();
        
        SessionMetrics saved = metricsRepository.save(metrics);
        log.debug("✓ Metrics saved: session={}, frames={}, features={}, avgLatency={:.2f}ms",
                sessionId, frameCount, featureCount, averageLatency);
        return saved;
    }
    
    // ========================================================================
    // AUDIT TRAIL
    // ========================================================================
    
    /**
     * Record audit event
     */
    public AuditEvent recordAuditEvent(String sessionId, String eventType, String message, String severity) {
        AuditEvent event = AuditEvent.builder()
                .sessionId(sessionId)
                .eventType(eventType)
                .eventMessage(message)
                .severity(severity)
                .build();
        
        AuditEvent saved = auditEventRepository.save(event);
        
        if ("error".equals(severity)) {
            log.warn("⚠️ Audit [{}]: {}", eventType, message);
        } else {
            log.debug("Audit [{}]: {}", eventType, message);
        }
        
        return saved;
    }
    
    /**
     * Record INFO audit event
     */
    public AuditEvent recordInfo(String sessionId, String eventType, String message) {
        return recordAuditEvent(sessionId, eventType, message, "info");
    }
    
    /**
     * Record WARNING audit event
     */
    public AuditEvent recordWarning(String sessionId, String eventType, String message) {
        return recordAuditEvent(sessionId, eventType, message, "warning");
    }
    
    /**
     * Record ERROR audit event
     */
    public AuditEvent recordError(String sessionId, String eventType, String message) {
        return recordAuditEvent(sessionId, eventType, message, "error");
    }
}

