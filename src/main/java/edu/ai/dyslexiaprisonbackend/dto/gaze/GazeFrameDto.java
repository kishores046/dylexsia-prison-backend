package edu.ai.dyslexiaprisonbackend.dto.gaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Real-time gaze frame data from eyetracking
 * 
 * Sent frequently (~60Hz) - minimize payload size
 * 
 * Fields:
 * - frameId: unique identifier for this frame
 * - timestamp: client-side timestamp (milliseconds)
 * - gazeX, gazeY: normalized gaze coordinates (0-1 scale)
 * - confidence: eyetracker confidence (0-1)
 * - pupilSize: pupil diameter in mm (optional)
 * 
 * Production notes:
 * - Use long for timestamp to reduce JSON size
 * - Confidence < 0.5 indicates unreliable frame
 * - Coordinates normalized to allow scaling to any screen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GazeFrameDto {
    
    @JsonProperty("frameId")
    private String frameId;
    
    @JsonProperty("timestamp")
    private Long timestamp;  // milliseconds
    
    @JsonProperty("gazeX")
    private Double gazeX;  // 0-1 normalized
    
    @JsonProperty("gazeY")
    private Double gazeY;  // 0-1 normalized
    
    @JsonProperty("confidence")
    private Double confidence;  // 0-1 quality metric
    
    @JsonProperty("pupilSize")
    private Double pupilSize;  // mm (optional)
    
    @JsonProperty("validFrame")
    private Boolean validFrame;  // true if gaze data is valid

    /**
     * Validate frame data before processing
     * @return true if all required fields are present and valid
     */
    public boolean isValid() {
        return frameId != null && !frameId.isBlank()
                && timestamp != null
                && gazeX != null && gazeX >= 0 && gazeX <= 1
                && gazeY != null && gazeY >= 0 && gazeY <= 1
                && confidence != null && confidence >= 0 && confidence <= 1;
    }
}

