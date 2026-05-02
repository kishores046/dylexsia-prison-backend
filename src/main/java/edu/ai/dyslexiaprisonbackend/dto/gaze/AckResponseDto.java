package edu.ai.dyslexiaprisonbackend.dto.gaze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Acknowledgment response sent back to client
 * 
 * Sent to /user/queue/ack after receiving frames/features
 * Helps client verify successful delivery
 * 
 * Status codes:
 * - "OK": message processed successfully
 * - "INVALID": validation failed
 * - "DUPLICATE": duplicate detection
 * - "RATE_LIMITED": too many messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AckResponseDto {
    
    @JsonProperty("id")
    private String id;  // references the original message ID
    
    @JsonProperty("status")
    private String status;  // "OK", "INVALID", "DUPLICATE", "RATE_LIMITED"
    
    @JsonProperty("message")
    private String message;  // human-readable description
    
    @JsonProperty("timestamp")
    private Long timestamp;  // server timestamp
}

