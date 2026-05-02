package edu.ai.dyslexiaprisonbackend.dto.gaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Session end event - signals end of gaze recording
 * 
 * Contains:
 * - sessionId: must match SessionStart sessionId
 * - frameCount: total frames collected
 * - featureCount: total features extracted
 * - metrics: summary statistics (average confidence, blink rate, etc.)
 * 
 * Usage:
 * Marks the end of a session and provides aggregated metrics
 * Triggers ML pipeline processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEndDto {
    
    @JsonProperty("sessionId")
    private String sessionId;  // must match SessionStart.sessionId
    
    @JsonProperty("frameCount")
    private Long frameCount;  // total frames in session
    
    @JsonProperty("featureCount")
    private Long featureCount;  // total features extracted
    
    @JsonProperty("durationMs")
    private Long durationMs;  // total session duration
    
    @JsonProperty("metrics")
    private Map<String, Object> metrics;  // {"avgConfidence": 0.85, "blinkRate": 15.2}
    
    /**
     * Validate session end
     */
    public boolean isValid() {
        return sessionId != null && !sessionId.isBlank()
                && frameCount != null && frameCount >= 0
                && durationMs != null && durationMs > 0;
    }
}

