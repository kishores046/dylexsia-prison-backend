package edu.ai.dyslexiaprisonbackend.service.buffer;

import edu.ai.dyslexiaprisonbackend.dto.gaze.FeaturePayloadDto;
import edu.ai.dyslexiaprisonbackend.dto.gaze.GazeFrameDto;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SessionBuffer - Thread-safe buffer for gaze frames and features
 * Stores:
 * - Deque of GazeFrameDto (FIFO order for batching)
 * - Deque of FeaturePayloadDto (FIFO order for batching)
 * - Timestamps for flush triggering
 * - Session metadata (taskId, metadata map)
 * 
 * Design:
 * - Bounded size to prevent OOM (configurable limits)
 * - Thread-safe using ConcurrentLinkedDeque
 * - Atomic counters for tracking
 * - Timestamp tracking for time-based flushing
 * 
 * FIX: Now stores taskId and sessionMetadata for batch processing
 * 
 * Production considerations:
 * - Prevents memory exhaustion from high-frequency streams
 * - Supports size-triggered and time-triggered batching
 * - Efficient FIFO operations for ordered batch processing
 */
@Slf4j
public class SessionBuffer {
    
    // Configuration
    private static final int MAX_FRAMES_PER_BUFFER = 500;
    private static final int MAX_FEATURES_PER_BUFFER = 100;
    
    @Getter
    private final String sessionId;
    
    @Getter
    private final String username;
    
    // FIX: Added to store session context for batch processing
    @Getter
    @Setter
    private String taskId;
    
    @Getter
    @Setter
    private Map<String, Object> sessionMetadata;
    
    // Data buffers (thread-safe)
    private final Deque<GazeFrameDto> frames;
    private final Deque<FeaturePayloadDto> features;
    
    // Counters
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicLong featureCount = new AtomicLong(0);
    
    // Flush tracking
    @Getter
    private volatile long lastFlushTimeMs;
    
    @Getter
    private volatile long createdTimeMs;
    
    /**
     * Initialize a new session buffer
     * 
     * @param sessionId unique session identifier
     * @param username authenticated user
     */
    public SessionBuffer(String sessionId, String username) {
        this.sessionId = sessionId;
        this.username = username;
        this.frames = new ConcurrentLinkedDeque<>();
        this.features = new ConcurrentLinkedDeque<>();
        this.lastFlushTimeMs = System.currentTimeMillis();
        this.createdTimeMs = System.currentTimeMillis();
        this.taskId = "task-unknown";  // Default value
        this.sessionMetadata = new HashMap<>();
    }
    
    /**
     * Add frame to buffer
     * 
     * Returns false if buffer is full (should trigger flush)
     * 
     * @param frame GazeFrameDto to add
     * @return true if added, false if buffer full
     */
    public boolean addFrame(GazeFrameDto frame) {
        if (frames.size() >= MAX_FRAMES_PER_BUFFER) {
            log.warn("⚠️ Frame buffer full for session {}: {}+ frames",
                    sessionId, frames.size());
            return false;
        }
        
        frames.offer(frame);
        frameCount.incrementAndGet();
        return true;
    }
    
    /**
     * Add feature to buffer
     * 
     * Returns false if buffer is full (should trigger flush)
     * 
     * @param feature FeaturePayloadDto to add
     * @return true if added, false if buffer full
     */
    public boolean addFeature(FeaturePayloadDto feature) {
        if (features.size() >= MAX_FEATURES_PER_BUFFER) {
            log.warn("⚠️ Feature buffer full for session {}: {}+ features",
                    sessionId, features.size());
            return false;
        }
        
        features.offer(feature);
        featureCount.incrementAndGet();
        return true;
    }
    
    /**
     * Check if buffer should flush based on frame count
     * 
     * Threshold: 200 frames
     * 
     * @return true if size limit reached
     */
    public boolean shouldFlushBySize() {
        return frames.size() >= 200;
    }
    
    /**
     * Check if buffer should flush based on time elapsed
     * 
     * Threshold: 2 seconds since last flush
     * 
     * @return true if time limit exceeded
     */
    public boolean shouldFlushByTime() {
        long elapsedMs = System.currentTimeMillis() - lastFlushTimeMs;
        return elapsedMs >= 2000;  // 2 seconds
    }
    
    /**
     * Check if buffer has any data
     * 
     * @return true if frames or features present
     */
    public boolean hasData() {
        return !frames.isEmpty() || !features.isEmpty();
    }
    
    /**
     * Get frame count
     */
    public int getFrameCount() {
        return frames.size();
    }
    
    /**
     * Get feature count
     */
    public int getFeatureCount() {
        return features.size();
    }
    
    /**
     * Drain all frames from buffer (for batch processing)
     * Clears internal queue and returns items
     * 
     * @return list of frames in order
     */
    public List<GazeFrameDto> drainFrames() {
        List<GazeFrameDto> list = new ArrayList<>();
        GazeFrameDto frame;
        while ((frame = frames.poll()) != null) {
            list.add(frame);
        }
        return list;
    }
    
    /**
     * Drain all features from buffer (for batch processing)
     * Clears internal queue and returns items
     * 
     * @return list of features in order
     */
    public List<FeaturePayloadDto> drainFeatures() {
        List<FeaturePayloadDto> list = new ArrayList<>();
        FeaturePayloadDto feature;
        while ((feature = features.poll()) != null) {
            list.add(feature);
        }
        return list;
    }
    
    /**
     * Update flush timestamp
     * Call after successful flush
     */
    public void updateFlushTime() {
        this.lastFlushTimeMs = System.currentTimeMillis();
    }
    
    /**
     * Clear buffer (for session end or error recovery)
     */
    public void clear() {
        frames.clear();
        features.clear();
        log.debug("✓ Session buffer cleared: {}", sessionId);
    }
    
    /**
     * Get buffer statistics for monitoring
     */
    public String getStats() {
        return String.format(
                "SessionBuffer[session=%s, frames=%d, features=%d, "
                + "frameCount=%d, featureCount=%d, lastFlushMs=%d, "
                + "shouldFlushBySize=%s, shouldFlushByTime=%s]",
                sessionId, frames.size(), features.size(),
                frameCount.get(), featureCount.get(),
                System.currentTimeMillis() - lastFlushTimeMs,
                shouldFlushBySize(), shouldFlushByTime()
        );
    }
}

