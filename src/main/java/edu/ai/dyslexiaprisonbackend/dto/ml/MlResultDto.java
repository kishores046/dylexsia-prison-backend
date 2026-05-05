package edu.ai.dyslexiaprisonbackend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlResultDto {

    @JsonProperty("sessionId")
    private String sessionId;

    // FIX: Python returns 0-100, not 0-1. Validate accordingly.
    @JsonProperty("riskScore")
    private Double riskScore;

    @JsonProperty("classification")
    private String classification;

    // Python returns 0-1 for confidence — this was correct already.
    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("breakdown")
    private Map<String, Double> breakdown;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Normalized 0-1 accessor for downstream use (e.g. WebSocket response to frontend)
    public double getRiskScoreNormalized() {
        if (riskScore == null) return 0.5;
        return Math.min(1.0, Math.max(0.0, riskScore / 100.0));
    }

    public boolean isValid() {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        // FIX: Python schema says riskScore is 0-100, not 0-1.
        // Your old check `riskScore > 1` rejected every valid ML response.
        if (riskScore == null || riskScore < 0 || riskScore > 100) {
            return false;
        }

        // FIX: validate classification against known Python Literal values
        if (classification == null || classification.isBlank()) {
            return false;
        }
        if (!classification.equals("LOW") && !classification.equals("MODERATE")
                && !classification.equals("HIGH")) {
            return false;
        }

        // confidence is 0-1 — this was correct, but make null explicit as valid
        // (Python AnalysisResponse always includes it, but be defensive)
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            return false;
        }

        // Auto-fix missing timestamp rather than rejecting
        if (timestamp == null || timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }

        return true;
    }
}