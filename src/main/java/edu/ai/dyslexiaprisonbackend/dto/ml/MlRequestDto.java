package edu.ai.dyslexiaprisonbackend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MlRequestDto - Request payload to ML service
 * 
 * Sent when buffer is flushed. Contains aggregated features (NOT raw frames).
 * 
 * Structure:
 * {
 *   "sessionId": "sess-123",
 *   "username": "john@example.com",
 *   "taskId": "reading-task-001",
 *   "features": {
 *     "avgFixationDuration": 285.5,
 *     "regressionRate": 5.8,
 *     "readingSpeed": 47.2,
 *     "verticalStability": 0.92,
 *     "skippedWordRate": 2.1
 *   },
 *   "metadata": {
 *     "sampleCount": 450,
 *     "duration": 30000
 *   }
 * }
 * 
 * Production considerations:
 * - Features are aggregated from raw frames (not sent raw)
 * - sessionId enables tracing in ML pipeline
 * - username for audit trail
 * - metadata for ML service context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRequestDto {
    
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("taskId")
    private String taskId;
    
    @JsonProperty("features")
    private Map<String, Double> features;  // aggregated numerical features
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;  // contextual data
    
    /**
     * Validate request before sending
     */
    public boolean isValid() {
        if (sessionId == null || sessionId.isBlank()) return false;
        if (username == null || username.isBlank()) return false;
        if (features == null || features.isEmpty()) return false;

        // FIX: guard against partial features reaching the ML service
        List<String> required = List.of(
                "avgFixationDuration", "maxFixationDuration", "saccadeCount",
                "regressionRate", "readingSpeed", "verticalStability", "skippedWordRate");
        List<String> missing = required.stream()
                .filter(k -> !features.containsKey(k))
                .toList();
        if (!missing.isEmpty()) {
            // will be caught by caller and logged before sending
            throw new IllegalStateException("MlRequestDto missing required features: " + missing);
        }

        return true;
    }
}

