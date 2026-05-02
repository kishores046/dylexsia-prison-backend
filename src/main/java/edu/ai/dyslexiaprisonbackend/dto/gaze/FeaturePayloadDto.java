package edu.ai.dyslexiaprisonbackend.dto.gaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Higher-level gaze features extracted from frame sequence
 * 
 * Sent periodically (fewer times than frames) when features are detected
 * 
 * Types of features:
 * - "fixation": sustained gaze at point
 * - "saccade": rapid eye movement
 * - "blink": eye closure event
 * - "pursuit": smooth following motion
 * 
 * Production notes:
 * - featureType determines payload interpretation
 * - metadata contains detailed measurements
 * - Used for ML pipeline ingestion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturePayloadDto {
    
    @JsonProperty("featureId")
    private String featureId;  // unique identifier
    
    @JsonProperty("featureType")
    private String featureType;  // "fixation", "saccade", "blink", "pursuit"
    
    @JsonProperty("startTimestamp")
    private Long startTimestamp;  // milliseconds
    
    @JsonProperty("endTimestamp")
    private Long endTimestamp;  // milliseconds
    
    @JsonProperty("centerX")
    private Double centerX;  // feature center position
    
    @JsonProperty("centerY")
    private Double centerY;
    
    @JsonProperty("duration")
    private Long duration;  // milliseconds
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;  // extensible payload
    
    /**
     * Validate high-level feature
     * @return true if required fields present and consistent
     */
    public boolean isValid() {
        if (featureId == null || featureId.isBlank()) return false;
        if (featureType == null || featureType.isBlank()) return false;
        if (startTimestamp == null || endTimestamp == null) return false;
        if (startTimestamp >= endTimestamp) return false;
        
        return centerX != null && centerX >= 0 && centerX <= 1
                && centerY != null && centerY >= 0 && centerY <= 1;
    }
}

