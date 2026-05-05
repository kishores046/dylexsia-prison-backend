package edu.ai.dyslexiaprisonbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;

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
 * - All errors are logged for debugging
 * 
 * FIX: Added proper @MessageExceptionHandler decorators
 * and routing to client error queue
 */
@ControllerAdvice
@Slf4j
public class WebSocketErrorHandler {

    /**
     * Handle validation errors (IllegalArgumentException)
     * Triggered when message validation fails
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleValidationError(IllegalArgumentException ex) {
        log.warn("⚠️ WebSocket validation error: {}", ex.getMessage());
        
        return Map.of(
            "error", "VALIDATION_ERROR",
            "message", "Message validation failed"
        );
    }

    /**
     * Handle JSON deserialization and type mismatch errors
     * Triggered when incoming message cannot be deserialized
     * to the expected DTO type
     */
    @MessageExceptionHandler(ClassCastException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleTypeConversionError(ClassCastException ex) {
        log.error("✗ WebSocket message type conversion error: {}", ex.getMessage());
        
        return Map.of(
            "error", "TYPE_ERROR",
            "message", "Message type is invalid"
        );
    }

    /**
     * Handle generic exceptions during message processing
     * Fallback for any other exceptions
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleGenericException(Exception ex) {
        log.error("✗ WebSocket error: {}", ex.getMessage(), ex);
        
        return Map.of(
            "error", "PROCESSING_ERROR",
            "message", "An error occurred while processing your message"
        );
    }
}


