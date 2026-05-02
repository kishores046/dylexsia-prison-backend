package edu.ai.dyslexiaprisonbackend.dto.gaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Session start event - signals beginning of gaze recording
 * 
 * Contains:
 * - sessionId: unique tracking ID for this session
 * - metadata: task info, screen resolution, eyetracker device, etc.
 * - userId: optional (can be derived from JWT principal)
 * 
 * Usage:
 * Marks the beginning of a measurement period
 * Helps correlate frames to specific tasks/documents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStartDto {
    
    @JsonProperty("sessionId")
    private String sessionId;  // UUID or unique identifier
    
    @JsonProperty("taskId")
    private String taskId;  // task being performed (reading test, etc.)
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;  // {"screenWidth": 1920, "screenHeight": 1080, "eyetracker": "Tobii"}
    
    /**
     * Validate session start
     */
    public boolean isValid() {
        return sessionId != null && !sessionId.isBlank()
                && taskId != null && !taskId.isBlank();
    }
}

