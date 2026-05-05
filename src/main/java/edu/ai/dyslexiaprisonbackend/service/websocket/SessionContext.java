package edu.ai.dyslexiaprisonbackend.service.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session context for an active WebSocket connection

 * Stores:
 * - sessionId: unique identifier for the gaze recording session
 * - username: authenticated user
 * - taskId: the task being performed during gaze recording
 * - connectionId: the WebSocket session ID
 * - startTime: when session was initiated
 * - metadata: additional session info (screen resolution, device, etc.)
 * - isActive: whether session is ongoing
 * - endTime: when session was ended (null if active)
 * 
 * Thread-safe for use with WebSocket sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {
    
    private String sessionId;
    private String username;
    private String taskId;
    private String connectionId;
    
    @Builder.Default
    private Long startTime = null;
    
    @Builder.Default
    private Long endTime = null;
    
    @Builder.Default
    private boolean isActive = true;
    
    private java.util.Map<String, Object> metadata;
    
    /**
     * Mark session as ended
     */
    public void end() {
        this.endTime = System.currentTimeMillis();
        this.isActive = false;
    }
    
    /**
     * Get session duration in milliseconds
     */
    public long getDurationMs() {
        long end = endTime != null ? endTime : System.currentTimeMillis();
        return end - startTime;
    }
    
    /**
     * Check if session is still active
     */
    public boolean isStillActive() {
        return isActive && endTime == null;
    }
}

