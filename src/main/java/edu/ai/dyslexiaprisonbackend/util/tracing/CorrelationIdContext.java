package edu.ai.dyslexiaprisonbackend.util.tracing;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * CorrelationIdContext - Thread-local correlation ID for distributed tracing
 * 
 * Provides traceability across async boundaries:
 * - WebSocket receive → buffer → aggregation → ML call → result routing
 * 
 * Usage:
 *   CorrelationIdContext.setId(UUID.randomUUID().toString());
 *   log.info("Processing frame"); // Auto-includes correlation ID via MDC
 *   CorrelationIdContext.clear(); // On session end
 */
@Slf4j
public class CorrelationIdContext {
    
    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    
    /**
     * Set correlation ID for current thread
     */
    public static void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or blank");
        }
        correlationId.set(id);
    }
    
    /**
     * Get or generate correlation ID (never null)
     */
    public static String getOrGenerate() {
        String id = correlationId.get();
        if (id == null) {
            id = UUID.randomUUID().toString();
            correlationId.set(id);
        }
        return id;
    }
    
    /**
     * Get current correlation ID (may be null)
     */
    public static String get() {
        return correlationId.get();
    }
    
    /**
     * Clear correlation ID
     */
    public static void clear() {
        correlationId.remove();
    }
    
    /**
     * Initialize for new request (generates UUID if not set)
     */
    public static String initialize() {
        String id = getOrGenerate();
        log.debug("[TRACE] Initialized correlation ID: {}", id);
        return id;
    }
}

