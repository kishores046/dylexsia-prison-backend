package edu.ai.dyslexiaprisonbackend.service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionContextService - Manages WebSocket connection-to-session mapping
 * Responsibilities:
 * - Track active WebSocket sessions and their gaze recording sessions
 * - Validate session state before processing messages
 * - Enforce single-session-per-connection rule
 * - Cleanup on disconnect
 * - Provide session lookup for message routing
 * Design:
 * - ConcurrentHashMap for thread-safe lookups
 * - Key = WebSocket connectionId (from StompHeaderAccessor)
 * - Value = SessionContext with sessionId, username, etc.
 * Production considerations:
 * - Prevents orphaned sessions (no forget to end)
 * - Enforces 1:1 connection:session mapping
 * - Automatic cleanup hooks for disconnections
 */
@Service
@Slf4j
public class SessionContextService {
    
    // Mapping: connectionId -> SessionContext
    private final ConcurrentHashMap<String, SessionContext> activeConnections = 
            new ConcurrentHashMap<>();
    
    /**
     * Create and register a new session for a connection
     * 
     * Validates:
     * - No duplicate session for this connection
     * - sessionId is not null
     * - username is not null
     * 
     * @param connectionId WebSocket session ID
     * @param sessionId unique gaze recording session ID
     * @param username authenticated user
     * @param taskId the task being performed
     * @param metadata session metadata (screen resolution, device)
     * @return created SessionContext
     * @throws IllegalStateException if connection already has an active session
     */
    public SessionContext startSession(
            String connectionId,
            String sessionId,
            String username,
            String taskId,
            java.util.Map<String, Object> metadata) {
        
        // Validate no duplicate session
        if (activeConnections.containsKey(connectionId)) {
            SessionContext existing = activeConnections.get(connectionId);
            if (existing.isStillActive()) {
                log.warn("⚠️ Connection {} already has active session: {}. "
                        + "Rejecting duplicate session start.", 
                        connectionId, existing.getSessionId());
                throw new IllegalStateException(
                        "Connection already has an active session");
            }
        }
        
        // Validate inputs
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        
        // Create and store context
        SessionContext context = SessionContext.builder()
                .sessionId(sessionId)
                .username(username)
                .taskId(taskId)
                .connectionId(connectionId)
                .metadata(metadata)
                .startTime(System.currentTimeMillis())
                .isActive(true)
                .build();
        
        activeConnections.put(connectionId, context);
        log.info("✓ Session started: connectionId={}, sessionId={}, user={}, task={}",
                connectionId, sessionId, username, taskId);
        
        return context;
    }
    
    /**
     * Get active session for a connection
     * 
     * @param connectionId WebSocket session ID
     * @return Optional containing SessionContext if found and active
     */
    public Optional<SessionContext> getSession(String connectionId) {
        SessionContext context = activeConnections.get(connectionId);
        
        if (context == null) {
            log.debug("⚠️ No session found for connectionId: {}", connectionId);
            return Optional.empty();
        }
        
        if (!context.isStillActive()) {
            log.debug("⚠️ Session for connectionId {} has already ended: {}",
                    connectionId, context.getSessionId());
            return Optional.empty();
        }
        
        return Optional.of(context);
    }
    
    /**
     * End session for a connection
     * 
     * Validates that the session being ended matches the stored one
     * (prevents cross-connection manipulation)
     * 
     * @param connectionId WebSocket session ID
     * @param expectedSessionId the sessionId being ended (for validation)
     * @return Optional containing ended SessionContext
     */
    public Optional<SessionContext> endSession(String connectionId, String expectedSessionId) {
        SessionContext context = activeConnections.get(connectionId);
        
        if (context == null) {
            log.warn("⚠️ No session found for connectionId: {}", connectionId);
            return Optional.empty();
        }
        
        // Validate session ID matches (prevent cross-session confusion)
        if (!context.getSessionId().equals(expectedSessionId)) {
            log.warn("⚠️ Session ID mismatch for connectionId {}. "
                    + "Expected: {}, Actual: {}",
                    connectionId, expectedSessionId, context.getSessionId());
            return Optional.empty();
        }
        
        // Mark as ended
        context.end();
        
        log.info("✓ Session ended: connectionId={}, sessionId={}, user={}, durationMs={}",
                connectionId, context.getSessionId(), context.getUsername(),
                context.getDurationMs());
        
        return Optional.of(context);
    }
    
    /**
     * Remove session from active tracking (after cleanup)
     * Call this after final processing is complete
     * 
     * @param connectionId WebSocket session ID
     */
    public void removeSession(String connectionId) {
        SessionContext removed = activeConnections.remove(connectionId);
        if (removed != null) {
            log.debug("✓ Session removed from tracking: connectionId={}, sessionId={}",
                    connectionId, removed.getSessionId());
        }
    }
    
    /**
     * Handle WebSocket disconnect
     * 
     * Should be called from @EventListener(SessionDisconnectEvent.class)
     * Ends any active session and cleans up
     * 
     * @param connectionId WebSocket session ID
     */
    public void handleDisconnect(String connectionId) {
        SessionContext context = activeConnections.get(connectionId);
        
        if (context != null) {
            if (context.isStillActive()) {
                log.warn("⚠️ Client disconnected with active session: {} (user: {})",
                        context.getSessionId(), context.getUsername());
                context.end();
            }
            
            removeSession(connectionId);
        }
    }
    
    /**
     * Get number of active connections
     * Useful for monitoring
     */
    public int getActiveConnectionCount() {
        return (int) activeConnections.values().stream()
                .filter(SessionContext::isStillActive)
                .count();
    }
    
    /**
     * Get number of active connections for a user
     * Useful for enforcing per-user connection limits
     */
    public int getUserConnectionCount(String username) {
        return (int) activeConnections.values().stream()
                .filter(ctx -> ctx.isStillActive() && username.equals(ctx.getUsername()))
                .count();
    }
}

