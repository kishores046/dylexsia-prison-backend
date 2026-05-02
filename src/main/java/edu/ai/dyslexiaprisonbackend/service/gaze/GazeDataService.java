package edu.ai.dyslexiaprisonbackend.service.gaze;

import edu.ai.dyslexiaprisonbackend.dto.gaze.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * GazeDataService - Manages gaze data collection and aggregation
 * 
 * Responsibilities:
 * - Session lifecycle (start, end)
 * - Frame buffering and aggregation
 * - Feature tracking
 * - Session metrics computation
 * - Forwarding to ML pipeline
 * 
 * Design:
 * - Non-blocking queues for high throughput
 * - Per-user session context
 * - Periodic aggregation for ML ingestion
 * 
 * Production ready for:
 * - Multiple concurrent users
 * - High-frequency frame data
 * - Batch processing to ML service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GazeDataService {

    // Queue frame data per session (user + sessionId)
    private final ConcurrentHashMap<String, GazeSessionContext> activeSessions = 
            new ConcurrentHashMap<>();

    /**
     * Start a new gaze recording session
     * 
     * @param username authenticated user
     * @param sessionStart session metadata
     */
    public void startSession(String username, SessionStartDto sessionStart) {
        String sessionKey = sessionKey(username, sessionStart.getSessionId());
        
        GazeSessionContext context = GazeSessionContext.builder()
                .sessionId(sessionStart.getSessionId())
                .username(username)
                .taskId(sessionStart.getTaskId())
                .metadata(sessionStart.getMetadata())
                .startTime(System.currentTimeMillis())
                .frames(new ConcurrentLinkedQueue<>())
                .features(new ConcurrentLinkedQueue<>())
                .build();

        activeSessions.put(sessionKey, context);
        log.info("✓ Session context created: {}", sessionKey);
    }

    /**
     * Enqueue incoming gaze frame
     * 
     * @param username authenticated user
     * @param sessionId current session
     * @param frame gaze frame data
     */
    public void enqueueFrame(String username, String sessionId, GazeFrameDto frame) {
        String sessionKey = sessionKey(username, sessionId);
        
        GazeSessionContext context = activeSessions.get(sessionKey);
        if (context == null) {
            log.warn("⚠️ No active session for key: {}", sessionKey);
            return;
        }

        context.getFrames().offer(frame);
        context.incrementFrameCount();

        // Update metrics
        context.updateConfidenceMetrics(frame.getConfidence());
    }

    /**
     * Enqueue detected gaze feature
     * 
     * @param username authenticated user
     * @param sessionId current session
     * @param feature detected feature (fixation, saccade, etc.)
     */
    public void enqueueFeature(String username, String sessionId, FeaturePayloadDto feature) {
        String sessionKey = sessionKey(username, sessionId);
        
        GazeSessionContext context = activeSessions.get(sessionKey);
        if (context == null) {
            log.warn("⚠️ No active session for key: {}", sessionKey);
            return;
        }

        context.getFeatures().offer(feature);
        context.incrementFeatureCount();
    }

    /**
     * End session and compute final metrics
     * 
     * @param username authenticated user
     * @param sessionEnd session summary
     * @return aggregated session data ready for ML pipeline
     */
    public GazeSessionContext endSession(String username, SessionEndDto sessionEnd) {
        String sessionKey = sessionKey(username, sessionEnd.getSessionId());
        
        GazeSessionContext context = activeSessions.remove(sessionKey);
        if (context == null) {
            log.warn("⚠️ Session not found for key: {}", sessionKey);
            return null;
        }

        context.setEndTime(System.currentTimeMillis());
        context.computeFinalMetrics();

        log.info("✓ Session ended and removed: {} (frames: {}, features: {})",
                sessionKey, context.getFrameCount(), context.getFeatureCount());

        // TODO: Forward to ML pipeline
        // mlPipelineService.submitAnalysisJob(context);

        return context;
    }

    /**
     * Get active session context (for monitoring/debugging)
     */
    public GazeSessionContext getSessionContext(String username, String sessionId) {
        return activeSessions.get(sessionKey(username, sessionId));
    }

    /**
     * Get all active sessions for a user (for monitoring)
     */
    public List<GazeSessionContext> getUserActiveSessions(String username) {
        return activeSessions.entrySet().stream()
                .filter(e -> e.getKey().startsWith(username + ":"))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Cleanup stale sessions (no activity for 30+ minutes)
     * Can be scheduled with @Scheduled
     */
    public void cleanupStaleSessions() {
        long thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000);
        
        activeSessions.entrySet().removeIf(entry -> {
            GazeSessionContext context = entry.getValue();
            if (context.getLastFrameTime() != null && 
                context.getLastFrameTime() < thirtyMinutesAgo) {
                log.warn("⚠️ Removed stale session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private String sessionKey(String username, String sessionId) {
        return username + ":" + sessionId;
    }
}

