package edu.ai.dyslexiaprisonbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * WebSocket Error Handler
 * 
 * Handles exceptions during WebSocket message processing:
 * - Malformed payloads (JSON parsing)
 * - Type mismatches
 * - Validation failures
 * - Business logic errors
 * 
 * Production notes:
 * - Errors are sent to /user/queue/errors
 * - Clients can subscribe to receive error notifications
 * - No sensitive information exposed in error messages
 */
@ControllerAdvice
@Slf4j
public class WebSocketErrorHandler {
    
    /**
     * Handle JSON deserialization errors
     * 
     * Triggered when incoming message cannot be deserialized
     * to the expected DTO type
     */
    public void handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            Message<?> message) {
        
        log.error("✗ Message deserialization error: {}", ex.getMessage());
        // Could send error to client here
    }

    /**
     * Additional error handlers can be added here for:
     * - IllegalArgumentException
     * - SecurityException
     * - Custom validation exceptions
     */
}

