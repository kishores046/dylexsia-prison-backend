package edu.ai.dyslexiaprisonbackend.service.gaze;

import edu.ai.dyslexiaprisonbackend.dto.gaze.FeaturePayloadDto;
import edu.ai.dyslexiaprisonbackend.dto.gaze.GazeFrameDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Session context holding gaze data for one recording session
 * 
 * Stores:
 * - Frames (high volume, temporary)
 * - Features (detected eye movements, fixations)
 * - Aggregated metrics
 * - Metadata
 * 
 * Thread-safe using concurrent collections.
 * Used by GazeDataService to manage session lifecycle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GazeSessionContext {
    
    private String sessionId;
    private String username;
    private String taskId;
    private Map<String, Object> metadata;
    
    // Timing
    @Builder.Default
    private Long startTime = null;
    
    @Builder.Default
    private Long endTime = null;
    
    @Builder.Default
    private Long lastFrameTime = null;
    
    // Counters
    @Builder.Default
    private Long frameCount = 0L;
    
    @Builder.Default
    private Long featureCount = 0L;
    
    // Data buffers
    @Builder.Default
    private Queue<GazeFrameDto> frames = new ConcurrentLinkedQueue<>();
    
    @Builder.Default
    private Queue<FeaturePayloadDto> features = new ConcurrentLinkedQueue<>();
    
    // Aggregated metrics
    @Builder.Default
    private Double avgConfidence = 0.0;
    
    @Builder.Default
    private Double minConfidence = 1.0;
    
    @Builder.Default
    private Double maxConfidence = 0.0;
    
    @Builder.Default
    private Long totalConfidenceSamples = 0L;
    
    @Builder.Default
    private Double confidenceSum = 0.0;
    
    /**
     * Increment frame counter and update last activity time
     */
    public void incrementFrameCount() {
        this.frameCount++;
        this.lastFrameTime = System.currentTimeMillis();
    }

    /**
     * Increment feature counter
     */
    public void incrementFeatureCount() {
        this.featureCount++;
    }

    /**
     * Track confidence metrics for aggregation
     */
    public void updateConfidenceMetrics(Double confidence) {
        if (confidence != null) {
            this.confidenceSum += confidence;
            this.totalConfidenceSamples++;
            this.minConfidence = Math.min(this.minConfidence, confidence);
            this.maxConfidence = Math.max(this.maxConfidence, confidence);
        }
    }

    /**
     * Compute final aggregated metrics at session end
     */
    public void computeFinalMetrics() {
        if (this.totalConfidenceSamples > 0) {
            this.avgConfidence = this.confidenceSum / this.totalConfidenceSamples;
        }
    }

    /**
     * Get session duration in milliseconds
     */
    public Long getDurationMs() {
        if (endTime != null) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get summary metrics as dictionary (for JSON serialization)
     */
    public Map<String, Object> getSummaryMetrics() {
        return Map.of(
                "sessionId", sessionId,
                "username", username,
                "taskId", taskId,
                "durationMs", getDurationMs(),
                "frameCount", frameCount,
                "featureCount", featureCount,
                "avgConfidence", avgConfidence,
                "minConfidence", minConfidence,
                "maxConfidence", maxConfidence
        );
    }

    /**
     * Drain all frames from buffer (for batch processing to ML)
     */
    public List<GazeFrameDto> drainFrames() {
        List<GazeFrameDto> list = new ArrayList<>();
        frames.forEach(list::add);
        frames.removeAll(list);
        return list;
    }

    /**
     * Drain all features from buffer (for batch processing to ML)
     */
    public List<FeaturePayloadDto> drainFeatures() {
        List<FeaturePayloadDto> list = new ArrayList<>();
        features.forEach(list::add);
        features.removeAll(list);
        return list;
    }
}

