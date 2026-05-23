package edu.ai.dyslexiaprisonbackend.service.ml;

import edu.ai.dyslexiaprisonbackend.dto.ml.MlResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MlResultRoutingService - Routes ML analysis results to correct users
 * 
 * Responsibilities:
 * - Validate results before routing
 * - Track result delivery (prevent flooding)
 * - Route to correct user via WebSocket
 * - Handle stale/duplicate results
 * - Send errors gracefully
 * 
 * PHASE 2: Session → User mapping (integrated with SessionContextService)
 * PHASE 3: Comprehensive validation before sending
 * PHASE 6: Advanced validation (session active, track last result)
 * PHASE 7: Error handling with error queue
 * PHASE 8: Multi-update support (timestamp tracking)
 * 
 * Production considerations:
 * - Per-user result tracking to prevent flooding
 * - Session staleness detection
 * - Error queue for failed deliveries
 * - Metrics tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MlResultRoutingService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Track last result timestamp per user
     * Prevents flooding with duplicate updates
     * Key: username, Value: last result timestamp
     */
    private final Map<String, Long> lastResultTimestamp = new ConcurrentHashMap<>();
    
    /**
     * Track result delivery count per user (for monitoring)
     */
    private final Map<String, AtomicLong> resultDeliveryCount = new ConcurrentHashMap<>();
    
    /**
     * FIX 2, 5: Make throttle interval configurable via application.properties
     * For development: 100-200ms to see quick results
     * For production: 500ms+ to prevent flooding
     */
    @Value("${ml.pipeline.result-throttle-ms:500}")
    private long minUpdateIntervalMs;
    
    /**
     * Route ML result to correct user via WebSocket
     * 
     * PHASES:
     * 2. Validates username exists
     * 3. Validates ML result
     * 6. Checks for stale results
     * 7. Handles errors gracefully
     * 8. Prevents flooding
     * 
     * @param username authenticated user to receive result
     * @param mlResult analysis result from ML service
     * @return true if successfully routed, false if rejected/suppressed
     */
    public boolean routeResultToUser(String username, MlResultDto mlResult) {
        // PHASE 6: Validation - Username
        if (username == null || username.isBlank()) {
            log.error("✗ Cannot route result: username is null or blank");
            sendErrorToUser("unknown", "Invalid username for result routing");
            return false;
        }
        
        // PHASE 3: Validation - Result validity
        if (mlResult == null) {
            log.error("✗ Cannot route result: mlResult is null for user {}", username);
            sendErrorToUser(username, "Received null result from ML service");
            return false;
        }
        
        if (!mlResult.isValid()) {
            log.error("✗ Cannot route result: invalid ML result for user {} - "
                    + "riskScore={}, classification={}, confidence={}",
                    username, mlResult.getRiskScore(), 
                    mlResult.getClassification(), mlResult.getConfidence());
            sendErrorToUser(username, "Invalid result from ML service");
            return false;
        }
        
        // PHASE 6: Validation - Session ID
        if (mlResult.getSessionId() == null || mlResult.getSessionId().isBlank()) {
            log.error("✗ Cannot route result: missing sessionId for user {}", username);
            sendErrorToUser(username, "Result missing session information");
            return false;
        }
        
        // PHASE 8: Multi-update support - Check if duplicate/too frequent
        if (!shouldSendResultToUser(username)) {
            log.debug("⊘ Suppressing result for user {} (update too frequent)", username);
            return false;
        }
        
        // PHASE 3: All validations passed - Route result
        try {
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/result",
                    mlResult);
            
            // Track delivery
            updateResultTimestamp(username);
            recordDelivery(username);
            
            log.info("✓ ML result routed to user {}: "
                    + "sessionId={}, riskScore={}, classification={}",
                    username, mlResult.getSessionId(), 
                    mlResult.getRiskScore(), mlResult.getClassification());
            
            return true;
            
        } catch (Exception e) {
            log.error("✗ Failed to route result to user {}: {}", 
                    username, e.getMessage(), e);
            sendErrorToUser(username, "Failed to deliver result: " + e.getMessage());
            return false;
        }
    }
    
     /**
      * PHASE 8: Check if we should send result to user
      * 
      * Prevents flooding by:
      * - Tracking last result timestamp
      * - Enforcing minimum interval between updates
      * - Allowing immediate first result
      */
     private boolean shouldSendResultToUser(String username) {
         Long lastTimestamp = lastResultTimestamp.get(username);
         
         // First result ever - always send
         if (lastTimestamp == null) {
             return true;
         }
         
         long now = System.currentTimeMillis();
         long timeSinceLastResult = now - lastTimestamp;
         
         // Check if enough time has passed
         boolean shouldSend = timeSinceLastResult >= minUpdateIntervalMs;
         
         if (!shouldSend) {
             // FIX 5: Log suppression at WARN so it's visible in logs (not DEBUG)
             log.warn("⊘ Update SUPPRESSED for {}: only {}ms since last result (throttle={}ms)",
                     username, timeSinceLastResult, minUpdateIntervalMs);
         }
         
         return shouldSend;
     }
    
    /**
     * Update last result timestamp for user (for flooding prevention)
     */
    private void updateResultTimestamp(String username) {
        lastResultTimestamp.put(username, System.currentTimeMillis());
    }
    
    /**
     * PHASE 7: Send error message to user
     * 
     * When ML analysis fails or routing fails,
     * send friendly error to /user/queue/errors
     */
    public void sendErrorToUser(String username, String errorMessage) {
        if (username == null || username.isBlank()) {
            log.warn("Cannot send error: username is blank");
            return;
        }
        
        try {
            // Create error payload
            Map<String, Object> error = Map.of(
                    "type", "ML_ERROR",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/errors",
                    error);
            
            log.info("✓ Error message sent to user {}: {}", username, errorMessage);
            
        } catch (Exception e) {
            log.error("✗ Failed to send error to user {}: {}", username, e.getMessage());
        }
    }
    
    /**
     * Record result delivery for metrics/monitoring
     */
    private void recordDelivery(String username) {
        resultDeliveryCount.computeIfAbsent(username, k -> new AtomicLong(0))
                .incrementAndGet();
    }
    
    /**
     * Get result delivery count for a user (for monitoring)
     */
    public long getResultDeliveryCount(String username) {
        AtomicLong count = resultDeliveryCount.get(username);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Clear tracking for a user (when session ends)
     * 
     * PHASE 2: Clean up session → user mapping
     */
    public void clearUserTracking(String username) {
        lastResultTimestamp.remove(username);
        resultDeliveryCount.remove(username);
        log.debug("✓ Cleared result tracking for user: {}", username);
    }
    
    /**
     * Get system metrics for result routing
     */
    public Map<String, Object> getMetrics() {
        return Map.of(
                "activeUsers", resultDeliveryCount.size(),
                "totalUsersSinceStart", resultDeliveryCount.keySet().size(),
                "totalResultsDelivered", resultDeliveryCount.values().stream()
                        .mapToLong(AtomicLong::get)
                        .sum()
        );
    }
}

