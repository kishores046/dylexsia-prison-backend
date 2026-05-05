package edu.ai.dyslexiaprisonbackend.controller.websocket;

import edu.ai.dyslexiaprisonbackend.dto.gaze.*;
import edu.ai.dyslexiaprisonbackend.service.buffer.SessionBufferService;
import edu.ai.dyslexiaprisonbackend.service.gaze.GazeDataService;
import edu.ai.dyslexiaprisonbackend.service.websocket.SessionContext;
import edu.ai.dyslexiaprisonbackend.service.websocket.SessionContextService;
import edu.ai.dyslexiaprisonbackend.util.ratelimit.WebSocketRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.stereotype.Controller;
import java.security.Principal;

/**
 * STOMP Message Controller for Gaze Data Streaming
 * 
 * Handles incoming gaze frames, features, and session lifecycle messages.
 * Uses STOMP's @MessageMapping for clean endpoint registration.
 * 
 * Flow:
 * 1. Client sends message to /app/gaze.frame
 * 2. Spring routes to @MessageMapping("/gaze.frame")
 * 3. Method validates and processes
 * 4. Response sent to /user/queue/ack
 * 
 * Production considerations:
 * - @AuthenticationPrincipal contains JWT-authenticated user
 * - All incoming data is validated before processing
 * - Rate limiting prevents resource exhaustion
 * - Logging at DEBUG level for frames (high volume)
 * - Logging at INFO level for features/sessions
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class GazeController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketRateLimiter rateLimiter;
    private final GazeDataService gazeDataService;
    private final SessionContextService sessionContextService;
    private final SessionBufferService sessionBufferService;

    /**
     * Handle incoming gaze frame
     * 
     * Endpoint: /app/gaze.frame
     * Publishes to: /user/queue/ack (acknowledgment)
     * 
     * Gaze frames arrive frequently (~60Hz), so:
     * - Validation is lightweight
     * - Rate limiting is enforced
     * - Minimal logging (DEBUG only)
     * - Frame is buffered for batch processing
     * 
     * @param frame GazeFrameDto with eye tracking data
     * @param principal authenticated principal
     * @param accessor StompHeaderAccessor to get connection ID
     */
    @MessageMapping("/gaze.frame")
    public void handleGazeFrame(
            @Payload GazeFrameDto frame,
            Principal principal,
            StompHeaderAccessor accessor) {

        // Extract username from principal (may be null in WebSocket context)
        String username = extractUsername(principal, accessor);
        if (username == null) {
            log.warn("✗ Frame received with no authenticated user");
            return;
        }
        
        String connectionId = accessor.getSessionId();

        // Rate limiting
        if (!rateLimiter.allowFrameMessage(username)) {
            log.warn("⚠️ Frame rate limit exceeded for user: {}", username);
            sendAckResponse(username, frame.getFrameId(), "RATE_LIMITED",
                    "Frame rate limit exceeded");
            return;
        }

        // Validation
        if (frame == null || !frame.isValid()) {
            log.warn("✗ Invalid gaze frame from user {}: {}", username,
                    frame != null ? frame.getFrameId() : "null");
            sendAckResponse(username, frame != null ? frame.getFrameId() : "unknown",
                    "INVALID", "Frame validation failed");
            return;
        }

        // Get session context
        var sessionOpt = sessionContextService.getSession(connectionId);
        if (sessionOpt.isEmpty()) {
            log.warn("✗ No active session for frame from user {}. "
                    + "Call gaze.session.start first.", username);
            sendAckResponse(username, frame.getFrameId(), "NO_SESSION",
                    "Session not started. Call gaze.session.start first.");
            return;
        }

        SessionContext session = sessionOpt.get();
        String sessionId = session.getSessionId();

        // Debug logging (high volume - only when needed)
        if (log.isDebugEnabled()) {
            log.debug("✓ Received gaze frame from {}: frameId={}, confidence={}, "
                    + "sessionId={}",
                    username, frame.getFrameId(), frame.getConfidence(), sessionId);
        }

        // Add to buffer for batch processing
        boolean buffered = sessionBufferService.addFrame(username, sessionId, frame);
        if (!buffered) {
            log.warn("✗ Failed to buffer frame for session: {}", sessionId);
            sendAckResponse(username, frame.getFrameId(), "BUFFER_FULL",
                    "Buffer full, please retry");
            return;
        }

        // Send acknowledgment
        sendAckResponse(username, frame.getFrameId(), "OK",
                "Frame received and buffered");
    }

    /**
     * Handle gaze feature (fixation, saccade, blink, pursuit)
     * 
     * Endpoint: /app/gaze.feature
     * Publishes to: /user/queue/ack
     * 
     * Features are sent less frequently than frames, so:
     * - More detailed validation
     * - Useful for immediate action
     * - Added to feature stream for ML
     * 
     * @param feature FeaturePayloadDto with detected feature
     * @param principal authenticated principal
     * @param accessor StompHeaderAccessor to get connection ID
     */
    @MessageMapping("/gaze.feature")
    public void handleGazeFeature(
            @Payload FeaturePayloadDto feature,
            Principal principal,
            StompHeaderAccessor accessor) {

        // Extract username from principal (may be null in WebSocket context)
        String username = extractUsername(principal, accessor);
        if (username == null) {
            log.warn("✗ Feature received with no authenticated user");
            return;
        }
        
        String connectionId = accessor.getSessionId();

        // Rate limiting (less strict for features)
        if (!rateLimiter.allowFeatureMessage(username)) {
            log.warn("⚠️ Feature rate limit exceeded for user: {}", username);
            sendAckResponse(username, feature.getFeatureId(), "RATE_LIMITED",
                    "Feature rate limit exceeded");
            return;
        }

        // Validation
        if (feature == null || !feature.isValid()) {
            log.warn("✗ Invalid gaze feature from user {}: {}", username,
                    feature != null ? feature.getFeatureId() : "null");
            sendAckResponse(username, feature != null ? feature.getFeatureId() : "unknown",
                    "INVALID", "Feature validation failed");
            return;
        }

        // Get session context
        var sessionOpt = sessionContextService.getSession(connectionId);
        if (sessionOpt.isEmpty()) {
            log.warn("✗ No active session for feature from user {}. "
                    + "Call gaze.session.start first.", username);
            sendAckResponse(username, feature.getFeatureId(), "NO_SESSION",
                    "Session not started. Call gaze.session.start first.");
            return;
        }

        SessionContext session = sessionOpt.get();
        String sessionId = session.getSessionId();

        // Info logging (moderate volume)
        log.info("✓ Received gaze feature from {}: type={}, duration={}ms, "
                + "sessionId={}",
                username, feature.getFeatureType(), feature.getDuration(), sessionId);

        // Add to buffer for batch processing
        boolean buffered = sessionBufferService.addFeature(username, sessionId, feature);
        if (!buffered) {
            log.warn("✗ Failed to buffer feature for session: {}", sessionId);
            sendAckResponse(username, feature.getFeatureId(), "BUFFER_FULL",
                    "Feature buffer full, please retry");
            return;
        }

        sendAckResponse(username, feature.getFeatureId(), "OK",
                "Feature received and buffered");
    }

    /**
     * Handle session start
     * Endpoint: /app/gaze.session.start
     * Publishes to: /user/queue/ack
     * Marks the beginning of data collection:
     * - Session metadata (screen resolution, device)
     * - Task information
     * - Timestamp correlation
     * 
     * @param sessionStart SessionStartDto with session metadata
     * @param principal authenticated principal
     * @param accessor StompHeaderAccessor to get connection ID
     */
    @MessageMapping("/gaze.session.start")
    public void handleSessionStart(
            @Payload SessionStartDto sessionStart,
           Principal principal,
            StompHeaderAccessor accessor) {

        // Extract username from principal (may be null in WebSocket context)
        String username = extractUsername(principal, accessor);
        if (username == null) {
            log.warn("✗ Session start received with no authenticated user");
            return;
        }
        
        String connectionId = accessor.getSessionId();

        if (sessionStart == null || !sessionStart.isValid()) {
            log.warn("✗ Invalid session start from user {}: {}", username,
                    sessionStart != null ? sessionStart.getSessionId() : "null");
            sendAckResponse(username, sessionStart != null ? sessionStart.getSessionId() : "unknown",
                    "INVALID", "Session start validation failed");
            return;
        }

        try {
            // Create session in context manager
            SessionContext context = sessionContextService.startSession(
                    connectionId,
                    sessionStart.getSessionId(),
                    username,
                    sessionStart.getTaskId(),
                    sessionStart.getMetadata()
            );

            // FIX: Create buffer with taskId and metadata (not hardcoded)
            sessionBufferService.getOrCreateBuffer(
                    sessionStart.getSessionId(), 
                    username,
                    sessionStart.getTaskId(),
                    sessionStart.getMetadata()
            );

            // Also register in legacy GazeDataService for metrics
            gazeDataService.startSession(username, sessionStart);

            log.info("→ Session STARTED for user {}: sessionId={}, taskId={}, "
                    + "connectionId={}",
                    username, sessionStart.getSessionId(), sessionStart.getTaskId(),
                    connectionId);

            sendAckResponse(username, sessionStart.getSessionId(), "OK",
                    "Session started successfully");

        } catch (IllegalStateException e) {
            log.warn("⚠️ Session start rejected: {}", e.getMessage());
            sendAckResponse(username, sessionStart.getSessionId(), "DUPLICATE",
                    "Connection already has an active session");
        } catch (Exception e) {
            log.error("✗ Error starting session for user {}: {}", username,
                    e.getMessage(), e);
            sendAckResponse(username, sessionStart.getSessionId(), "ERROR",
                    "Failed to start session");
        }
    }

    /**
     * Handle session end
     * Endpoint: /app/gaze.session.end
     * Publishes to: /user/queue/ack
     * Marks end of data collection:
     * - Final frame count
     * - Aggregated metrics
     * - Signals ML pipeline to process
     * 
     * @param sessionEnd SessionEndDto with session summary
     * @param principal authenticated principal
     * @param accessor StompHeaderAccessor to get connection ID
     */
    @MessageMapping("/gaze.session.end")
    public void handleSessionEnd(
            @Payload SessionEndDto sessionEnd,
            Principal principal,
            StompHeaderAccessor accessor) {

        // Extract username from principal (may be null in WebSocket context)
        String username = extractUsername(principal, accessor);
        if (username == null) {
            log.warn("✗ Session end received with no authenticated user");
            return;
        }
        
        String connectionId = accessor.getSessionId();

        if (sessionEnd == null || !sessionEnd.isValid()) {
            log.warn("✗ Invalid session end from user {}: {}", username,
                    sessionEnd != null ? sessionEnd.getSessionId() : "null");
            sendAckResponse(username, sessionEnd != null ? sessionEnd.getSessionId() : "unknown",
                    "INVALID", "Session end validation failed");
            return;
        }

        try {
            // End session in context manager
            var sessionOpt = sessionContextService.endSession(
                    connectionId,
                    sessionEnd.getSessionId()
            );

            if (sessionOpt.isEmpty()) {
                log.warn("⚠️ Session not found or already ended: {}",
                        sessionEnd.getSessionId());
                sendAckResponse(username, sessionEnd.getSessionId(), "NO_SESSION",
                        "Session not found");
                return;
            }

            // Flush any remaining data from buffer
            sessionBufferService.flush(sessionEnd.getSessionId(), "SESSION_END");

            // Also end in legacy GazeDataService
            gazeDataService.endSession(username, sessionEnd);

            log.info("← Session ENDED for user {}: sessionId={}, frames={}, "
                    + "features={}, duration={}ms",
                    username, sessionEnd.getSessionId(), sessionEnd.getFrameCount(),
                    sessionEnd.getFeatureCount(), sessionEnd.getDurationMs());

            sendAckResponse(username, sessionEnd.getSessionId(), "OK",
                    "Session ended - analysis queued");

        } catch (Exception e) {
            log.error("✗ Error ending session for user {}: {}", username,
                    e.getMessage(), e);
            sendAckResponse(username, sessionEnd.getSessionId(), "ERROR",
                    "Failed to end session");
        }
    }

    /**
     * Send acknowledgment response to client
     * 
     * Sent to /user/queue/ack for client to subscribe to
     * Helps verify message delivery and processing
     * 
     * @param username target user
     * @param messageId ID from incoming message
     * @param status "OK", "INVALID", "RATE_LIMITED", etc.
     * @param message human-readable status message
     */
    private void sendAckResponse(String username, String messageId, String status, String message) {
        AckResponseDto ack = AckResponseDto.builder()
                .id(messageId)
                .status(status)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(username, "/queue/ack", ack);
    }

    /**
     * Extract username from Principal or StompHeaderAccessor
     * 
     * FIX: WebSocketAuthInterceptor now properly attaches authentication to ALL STOMP messages.
     * For CONNECT: Direct principal injection works
     * For subsequent messages: accessor.getUser() is populated by WebSocketAuthInterceptor
     * 
     * @param principal optional Principal from parameter injection (CONNECT only)
     * @param accessor StompHeaderAccessor containing cached authentication
     * @return username or null if authentication not available
     */
    private String extractUsername(Principal principal, StompHeaderAccessor accessor) {
        // Strategy 1: Try direct principal first (works for CONNECT frame)
        if (principal != null) {
            String name = principal.getName();
            if (name != null && !name.isBlank()) {
                log.debug("✓ Extracted username from principal parameter: {}", name);
                return name;
            }
        }

        // Strategy 2: Try extracting from accessor's user principal
        // FIX: Now works for ALL messages because WebSocketAuthInterceptor attaches auth
        if (accessor != null) {
            Principal user = accessor.getUser();
            if (user != null) {
                String name = user.getName();
                if (name != null && !name.isBlank()) {
                    log.debug("✓ Extracted username from accessor.user: {}", name);
                    return name;
                }
            }
            log.warn("⚠️ accessor.getUser() returned null");
        } else {
            log.warn("⚠️ StompHeaderAccessor is null!");
        }


        return null;
    }
}

