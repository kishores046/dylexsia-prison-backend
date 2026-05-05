package edu.ai.dyslexiaprisonbackend.exception;


import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for REST API
 * 
 * Handles:
 * - Validation errors (MethodArgumentNotValidException)
 * - Authentication failures (AuthenticationFailedException)
 * - Refresh token errors (RefreshTokenException)
 * - User not found (UserNotFoundException)
 * - Generic exceptions
 * 
 * PRODUCTION FIXES:
 * - Does not expose sensitive error information
 * - Logs full stack trace internally
 * - Returns generic error messages to client
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle DTO validation errors
     * Triggered when @Valid fails on request payload
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull Map<String,Object>> handleInvalidDataInDto(
            MethodArgumentNotValidException ex){
        
        Map<String,String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("⚠️ Validation error: {}", errors);
        
        return new ResponseEntity<>(Map.of(
                "error", "VALIDATION_ERROR",
                "message", "Request validation failed",
                "details", errors
        ), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle authentication failures (invalid credentials, etc.)
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<@NonNull Map<String, String>> handleAuthenticationFailed(
            AuthenticationFailedException ex) {
        
        log.warn("⚠️ Authentication failed: {}", ex.getMessage());
        
        return new ResponseEntity<>(Map.of(
                "error", "AUTHENTICATION_FAILED",
                "message", "Invalid credentials"
        ), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle refresh token errors
     */
    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<@NonNull Map<String, String>> handleRefreshTokenError(
            RefreshTokenException ex) {
        
        log.warn("⚠️ Refresh token error: {}", ex.getMessage());
        
        return new ResponseEntity<>(Map.of(
                "error", "REFRESH_TOKEN_ERROR",
                "message", "Refresh token is invalid or expired"
        ), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle user not found errors
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<@NonNull Map<String, String>> handleUserNotFound(
            UserNotFoundException ex) {
        
        log.warn("⚠️ User not found: {}", ex.getMessage());
        
        return new ResponseEntity<>(Map.of(
                "error", "USER_NOT_FOUND",
                "message", "User does not exist"
        ), HttpStatus.NOT_FOUND);
    }

    /**
     * Handle generic exceptions
     * Should rarely reach here (specific handlers above)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull Map<String, String>> handleGenericException(
            Exception ex) {
        
        log.error("✗ Unexpected error: {}", ex.getMessage(), ex);
        
        return new ResponseEntity<>(Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected error occurred"
        ), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
