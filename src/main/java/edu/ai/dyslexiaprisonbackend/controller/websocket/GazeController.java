package edu.ai.dyslexiaprisonbackend.controller.websocket;

import edu.ai.dyslexiaprisonbackend.dto.gaze.*;
import edu.ai.dyslexiaprisonbackend.service.gaze.GazeDataService;
import edu.ai.dyslexiaprisonbackend.util.ratelimit.WebSocketRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

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
     * - Frame is queued for ML pipeline
     * 
     * @param frame GazeFrameDto with eye tracking data
     * @param userDetails authenticated principal
     */
    @MessageMapping("/gaze.frame")
    public void handleGazeFrame(
            @Payload GazeFrameDto frame,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();

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

        // Debug logging (high volume - only when needed)
        if (log.isDebugEnabled()) {
            log.debug("✓ Received gaze frame from {}: frameId={}, confidence={}",
                    username, frame.getFrameId(), frame.getConfidence());
        }

        // Queue frame for later ML pipeline processing
        // Session ID is derived from current connection context
        // For now, frames are buffered - in production, extract sessionId from header
        String sessionId = extractSessionIdFromContext();  // implementation varies
        gazeDataService.enqueueFrame(username, sessionId, frame);

        // Send acknowledgment
        sendAckResponse(username, frame.getFrameId(), "OK",
                "Frame received and queued");
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
     * @param userDetails authenticated principal
     */
    @MessageMapping("/gaze.feature")
    public void handleGazeFeature(
            @Payload FeaturePayloadDto feature,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();

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

        // Info logging (moderate volume)
        log.info("✓ Received gaze feature from {}: type={}, duration={}ms",
                username, feature.getFeatureType(), feature.getDuration());

        // Enqueue feature for later ML pipeline processing
        String sessionId = extractSessionIdFromContext();  // implementation varies
        gazeDataService.enqueueFeature(username, sessionId, feature);

        sendAckResponse(username, feature.getFeatureId(), "OK",
                "Feature received and queued");
    }

    /**
     * Handle session start
     * 
     * Endpoint: /app/gaze.session.start
     * Publishes to: /user/queue/ack
     * 
     * Marks the beginning of data collection:
     * - Session metadata (screen resolution, device)
     * - Task information
     * - Timestamp correlation
     * 
     * @param sessionStart SessionStartDto with session metadata
     * @param userDetails authenticated principal
     */
    @MessageMapping("/gaze.session.start")
    public void handleSessionStart(
            @Payload SessionStartDto sessionStart,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();

        // Validation
        if (sessionStart == null || !sessionStart.isValid()) {
            log.warn("✗ Invalid session start from user {}: {}", username,
                    sessionStart != null ? sessionStart.getSessionId() : "null");
            sendAckResponse(username, sessionStart != null ? sessionStart.getSessionId() : "unknown",
                    "INVALID", "Session start validation failed");
            return;
        }

        log.info("→ Session STARTED for user {}: sessionId={}, taskId={}", 
                username, sessionStart.getSessionId(), sessionStart.getTaskId());

        // Initialize session context
        gazeDataService.startSession(username, sessionStart);

        sendAckResponse(username, sessionStart.getSessionId(), "OK",
                "Session started");
    }

    /**
     * Handle session end
     * 
     * Endpoint: /app/gaze.session.end
     * Publishes to: /user/queue/ack
     * 
     * Marks end of data collection:
     * - Final frame count
     * - Aggregated metrics
     * - Signals ML pipeline to process
     * 
     * @param sessionEnd SessionEndDto with session summary
     * @param userDetails authenticated principal
     */
    @MessageMapping("/gaze.session.end")
    public void handleSessionEnd(
            @Payload SessionEndDto sessionEnd,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();

        // Validation
        if (sessionEnd == null || !sessionEnd.isValid()) {
            log.warn("✗ Invalid session end from user {}: {}", username,
                    sessionEnd != null ? sessionEnd.getSessionId() : "null");
            sendAckResponse(username, sessionEnd != null ? sessionEnd.getSessionId() : "unknown",
                    "INVALID", "Session end validation failed");
            return;
        }

        log.info("← Session ENDED for user {}: sessionId={}, frames={}, features={}, duration={}ms",
                username, sessionEnd.getSessionId(), sessionEnd.getFrameCount(),
                sessionEnd.getFeatureCount(), sessionEnd.getDurationMs());

        // Finalize session and trigger ML pipeline analysis
        gazeDataService.endSession(username, sessionEnd);

        sendAckResponse(username, sessionEnd.getSessionId(), "OK",
                "Session ended - analysis queued");
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
     * Extract session ID from connection context
     * 
     * In production, this would be extracted from:
     * - WebSocket connection headers
     * - StompPrincipal attributes
     * - User session store
     * 
     * For now, using a placeholder UUID
     */
    private String extractSessionIdFromContext() {
        // TODO: Implement proper session context management
        // For now, return a placeholder - ideally stored in WebSocket session
        return "default-session-id";
    }
}

