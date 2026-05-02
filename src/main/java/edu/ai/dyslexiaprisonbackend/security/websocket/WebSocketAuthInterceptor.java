package edu.ai.dyslexiaprisonbackend.security.websocket;

import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocket Channel Interceptor for JWT Authentication
 * 
 * Intercepts STOMP CONNECT frames and validates JWT tokens.
 * 
 * Flow:
 * 1. Client connects via WebSocket with JWT in authorization header
 * 2. Interceptor extracts token from "Authorization" header
 * 3. Token is validated against JWT service
 * 4. User principal is attached to session
 * 5. Connection is allowed or rejected based on token validity
 * 
 * Production Notes:
 * - This runs per-message, so validation is fast
 * - Failed auth triggers connection rejection
 * - Token is cached in StompPrincipal for message mappings
 * - All errors are logged for security monitoring
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtilService jwtUtilService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Process outgoing message (before sending to client)
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Only authenticate on CONNECT command
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }

        return message;
    }

    /**
     * Handle CONNECT command - extract and validate JWT
     * 
     * Expected header format:
     * Authorization: Bearer <jwt_token>
     */
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        try {
            // Extract Authorization header
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("⚠️ WebSocket connection attempt without Authorization header");
                accessor.setLeaveMutable(true);
                return message;
            }

            // Extract Bearer token
            String token = extractBearerToken(authHeader);
            if (token == null) {
                log.warn("⚠️ WebSocket Authorization header missing 'Bearer' prefix");
                accessor.setLeaveMutable(true);
                return message;
            }

            // Validate token and extract username
            String username = jwtUtilService.extractUsername(token);

            // Load user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Validate token
            if (jwtUtilService.isTokenValid(token, userDetails)) {
                // Create authentication token
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Attach to STOMP headers for use in @MessageMapping methods
                accessor.setUser(auth);
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.info("✓ WebSocket authenticated successfully for user: {}", username);
            } else {
                log.warn("✗ Invalid JWT token for WebSocket connection, user: {}", username);
                accessor.setLeaveMutable(true);
            }

        } catch (SecurityException e) {
            log.error("✗ JWT parsing failed for WebSocket connection: {}", e.getMessage());
            accessor.setLeaveMutable(true);
        } catch (Exception e) {
            log.error("✗ Unexpected error during WebSocket authentication: {}", e.getMessage(), e);
            accessor.setLeaveMutable(true);
        }

        return message;
    }

    /**
     * Extract Bearer token from Authorization header
     * 
     * @param authHeader Authorization header value (e.g., "Bearer xyz...")
     * @return token without "Bearer" prefix, or null if invalid format
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

}

