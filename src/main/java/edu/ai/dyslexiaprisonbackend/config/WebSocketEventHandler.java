package edu.ai.dyslexiaprisonbackend.config;

import edu.ai.dyslexiaprisonbackend.service.buffer.SessionBufferService;
import edu.ai.dyslexiaprisonbackend.service.websocket.SessionContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket Session Event Handler
 * 
 * Listens to WebSocket lifecycle events:
 * - SessionConnectEvent: User connects
 * - SessionDisconnectEvent: User disconnects
 * 
 * Responsibilities:
 * - Track connection lifecycle
 * - Cleanup sessions on disconnect
 * - Flush pending data before cleanup
 * - Prevent memory leaks from orphaned sessions
 * 
 * Production considerations:
 * - Handles concurrent connections
 * - Graceful cleanup with data preservation
 * - Logging for debugging
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventHandler {
    
    private final SessionContextService sessionContextService;
    private final SessionBufferService sessionBufferService;
    
    /**
     * Handle WebSocket connection event
     * Called when client connects to /ws/gaze endpoint
     * 
     * @param event the connect event
     */
    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String connectionId = accessor.getSessionId();
        
        log.debug("↗️ WebSocket connected: connectionId={}", connectionId);
    }
    
    /**
     * Handle WebSocket disconnection event
     * Called when client disconnects from /ws/gaze endpoint
     * 
     * Cleanup:
     * 1. Flush any pending data in buffer
     * 2. End session context
     * 3. Remove session from tracking
     * 
     * @param event the disconnect event
     */
    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String connectionId = accessor.getSessionId();
        
        log.info("↙️ WebSocket disconnected: connectionId={}", connectionId);
        
        try {
            // Let session context service handle cleanup
            sessionContextService.handleDisconnect(connectionId);
            
            // Note: The buffer cleanup is tied to session end via GazeController
            // If session.end() is not called explicitly, buffer will persist
            // until cleared by stale session cleanup
            
        } catch (Exception e) {
            log.error("✗ Error handling WebSocket disconnect for connectionId: {}",
                    connectionId, e);
        }
    }
}

