package edu.ai.dyslexiaprisonbackend.service.buffer;

import edu.ai.dyslexiaprisonbackend.dto.gaze.FeaturePayloadDto;
import edu.ai.dyslexiaprisonbackend.dto.gaze.GazeFrameDto;
import edu.ai.dyslexiaprisonbackend.service.ml.MlResultRoutingService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionBufferService - Manages buffering for multiple concurrent gaze sessions
 * Responsibilities:
 * - Maintain per-session buffers (Map<sessionId, SessionBuffer>)
 * - Add frames and features to appropriate buffers
 * - Detect and trigger batch flushing
 * - Coordinate with batch processor
 * - Cleanup buffers on session end
 * Design:
 * - ConcurrentHashMap for thread-safe access
 * - Key = sessionId (from SessionContext)
 * - Value = SessionBuffer with gaze data
 * - Integrates with GazeBatchProcessor for ML pipeline
 * Production considerations:
 * - Handles hundreds of concurrent users
 * - Non-blocking operations for real-time streams
 * - Configurable batch thresholds
 * - Monitoring APIs for status/health
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionBufferService {
    
    // Mapping: sessionId -> SessionBuffer
    private final ConcurrentHashMap<String, SessionBuffer> buffers = 
            new ConcurrentHashMap<>();
    
    // Batch processor for flushed data
    private final GazeBatchProcessor batchProcessor;
    
    // Result routing service for cleanup
    private final MlResultRoutingService resultRoutingService;
    
    /**
     * Get or create buffer for a session
     * 
     * FIX: Now accepts taskId and metadata for proper batch processing
     * 
     * @param sessionId unique session ID
     * @param username authenticated user
     * @param taskId the task being performed (reading test, etc.)
     * @param metadata session metadata (screen resolution, device, etc.)
     * @return SessionBuffer for the session
     */
    public @NonNull SessionBuffer getOrCreateBuffer(String sessionId, String username, 
                                                     String taskId, java.util.Map<String, Object> metadata) {
        return buffers.computeIfAbsent(sessionId, key -> {
            SessionBuffer buffer = new SessionBuffer(sessionId, username);
            // FIX: Store taskId and metadata for batch processing
            buffer.setTaskId(taskId != null ? taskId : "task-unknown");
            buffer.setSessionMetadata(metadata != null ? metadata : new HashMap<>());
            log.debug("✓ Created new session buffer: {}", sessionId);
            return buffer;
        });
    }
    
    /**
     * Legacy: Get or create buffer (for backward compatibility)
     * 
     * @param sessionId unique session ID
     * @param username authenticated user
     * @return SessionBuffer for the session
     */
    public @NonNull SessionBuffer getOrCreateBuffer(String sessionId, String username) {
        return getOrCreateBuffer(sessionId, username, "task-unknown", new HashMap<>());
    }
    
    /**
     * Add frame to session buffer
     * Triggers flush if buffer is full
     * 
     * @param username authenticated user
     * @param sessionId session identifier
     * @param frame the gaze frame
     * @return true if added successfully, false if buffer full
     */
    public boolean addFrame(String username, String sessionId, GazeFrameDto frame) {
        SessionBuffer buffer = buffers.get(sessionId);
        
        if (buffer == null) {
            log.warn("⚠️ No buffer found for session: {}", sessionId);
            return false;
        }
        
        boolean added = buffer.addFrame(frame);
        
        if (!added) {
            log.info("⚠️ Session buffer full for session: {}. Triggering flush.", sessionId);
            flush(sessionId, "SIZE_LIMIT");
            
            // Try adding again after flush
            added = buffer.addFrame(frame);
        }
        
        // Check time-based trigger
        if (added && buffer.shouldFlushByTime()) {
            log.debug("✓ Time-based flush triggered for session: {}", sessionId);
            flush(sessionId, "TIME_LIMIT");
        }
        
        return added;
    }
    
    /**
     * Add feature to session buffer
     * 
     * Triggers flush if buffer is full
     * 
     * @param username authenticated user
     * @param sessionId session identifier
     * @param feature the detected feature
     * @return true if added successfully, false if buffer full
     */
    public boolean addFeature(String username, String sessionId, FeaturePayloadDto feature) {
        SessionBuffer buffer = buffers.get(sessionId);
        
        if (buffer == null) {
            log.warn("⚠️ No buffer found for session: {}", sessionId);
            return false;
        }
        
        boolean added = buffer.addFeature(feature);
        
        if (!added) {
            log.info("⚠️ Feature buffer full for session: {}. Triggering flush.", sessionId);
            flush(sessionId, "SIZE_LIMIT");
            
            // Try adding again after flush
            added = buffer.addFeature(feature);
        }
        
        return added;
    }
    
    /**
     * Manually flush buffer for a session
     * Triggers batch processing to ML pipeline
     * Updates flush timestamp
     * 
     * FIX: Now uses actual taskId and metadata from buffer
     * 
     * @param sessionId session identifier
     * @param trigger reason for flush ("SIZE_LIMIT", "TIME_LIMIT", "SESSION_END", etc.)
     */
    public void flush(String sessionId, String trigger) {
        SessionBuffer buffer = buffers.get(sessionId);
        
        if (buffer == null) {
            log.debug("⚠️ No buffer to flush for session: {}", sessionId);
            return;
        }
        
        if (!buffer.hasData()) {
            log.debug("⚠️ No data to flush for session: {}", sessionId);
            return;
        }
        
        // Drain data from buffer
        List<GazeFrameDto> frames = buffer.drainFrames();
        List<FeaturePayloadDto> features = buffer.drainFeatures();
        
        log.info("✓ Flushing session buffer: sessionId={}, trigger={}, "
                + "frames={}, features={}",
                sessionId, trigger, frames.size(), features.size());
        
        // FIX: Use actual taskId and metadata from buffer
        batchProcessor.processBatch(
                sessionId,
                buffer.getUsername(),
                buffer.getTaskId(),
                frames,
                features,
                buffer.getSessionMetadata()
        );
        
        // Update flush time
        buffer.updateFlushTime();
    }
    
    /**
     * Get buffer for a session (read-only for monitoring)
     * 
     * @param sessionId session identifier
     * @return Optional containing SessionBuffer if found
     */
    public Optional<SessionBuffer> getBuffer(String sessionId) {
        return Optional.ofNullable(buffers.get(sessionId));
    }
    
    /**
     * Remove buffer (called on session end)
     * 
     * IMPORTANT: Flush first to ensure no data loss
     * Also cleans up result routing tracking for user
     * 
     * @param sessionId session identifier
     */
    public void removeBuffer(String sessionId) {
        SessionBuffer buffer = buffers.remove(sessionId);
        
        if (buffer != null) {
            // Ensure any remaining data is flushed
            if (buffer.hasData()) {
                log.warn("⚠️ Removing buffer with data still pending. "
                        + "Forcing final flush: {}", sessionId);
                flush(sessionId, "SESSION_END");
            }
            
            // PHASE 2: Clean up result routing tracking for user
            resultRoutingService.clearUserTracking(buffer.getUsername());
            
            buffer.clear();
            log.debug("✓ Session buffer removed: {}", sessionId);
        }
    }
    
    /**
     * Clear all buffers (for testing or emergency shutdown)
     */
    public void clearAll() {
        buffers.values().forEach(SessionBuffer::clear);
        buffers.clear();
        log.warn("⚠️ All session buffers cleared");
    }
    
    /**
     * Get number of active buffers
     * Useful for monitoring
     */
    public int getActiveBufferCount() {
        return buffers.size();
    }
    
    /**
     * Get statistics for all buffers (for monitoring)
     * 
     * @return list of buffer statistics
     */
    public List<String> getAllBufferStats() {
        return buffers.values().stream()
                .map(SessionBuffer::getStats)
                .toList();
    }
    
    /**
     * Get total pending frames across all buffers
     * Useful for monitoring backlog
     */
    public int getTotalPendingFrames() {
        return buffers.values().stream()
                .mapToInt(SessionBuffer::getFrameCount)
                .sum();
    }
    
    /**
     * Get total pending features across all buffers
     * Useful for monitoring backlog
     */
    public int getTotalPendingFeatures() {
        return buffers.values().stream()
                .mapToInt(SessionBuffer::getFeatureCount)
                .sum();
    }
    
    /**
     * Flush all buffers that should flush based on time
     * Can be called by a scheduled task
     */
    public void flushStaleBuffers() {
        int flushed = 0;
        for (Map.Entry<String, SessionBuffer> entry : buffers.entrySet()) {
            if (entry.getValue().shouldFlushByTime()) {
                flush(entry.getKey(), "SCHEDULED_FLUSH");
                flushed++;
            }
        }
        
        if (flushed > 0) {
            log.debug("✓ Flushed {} stale buffers", flushed);
        }
    }
}

