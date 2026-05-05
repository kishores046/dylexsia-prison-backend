package edu.ai.dyslexiaprisonbackend.security.websocket;

import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtilService jwtUtilService;
    private final CustomUserDetailsService userDetailsService;

    private static final Map<String, UsernamePasswordAuthenticationToken> authenticationCache =
            new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        // CRITICAL: must use getMutableAccessor so header mutations are reflected
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();
        StompCommand command = accessor.getCommand();

        log.debug("📨 STOMP command: {}, sessionId: {}", command, sessionId);

        if (StompCommand.CONNECT.equals(command)) {
            return handleConnect(message, accessor, sessionId);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            return handleDisconnect(message, sessionId);
        } else {
            return attachCachedAuthentication(message, accessor, sessionId);
        }
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor, String sessionId) {
        try {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("⚠️ WebSocket connection attempt without Authorization header");
                return null;
            }

            String token = extractBearerToken(authHeader);
            if (token == null) {
                log.warn("⚠️ WebSocket Authorization header missing 'Bearer' prefix");
                return null;
            }

            String username = jwtUtilService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!jwtUtilService.isTokenValid(token, userDetails)) {
                log.warn("✗ Invalid JWT token for WebSocket connection");
                return null;
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            // FIX: setLeaveMutable BEFORE setUser, then rebuild the message
            accessor.setLeaveMutable(true);
            accessor.setUser(auth);

            authenticationCache.put(sessionId, auth);
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.info("✅ WebSocket authenticated: user={}, sessionId={}", username, sessionId);

            // FIX: rebuild the message so the mutated accessor is actually used
            return MessageBuilder.fromMessage(message).setHeaders(accessor).build();

        } catch (SecurityException e) {
            log.warn("⚠️ JWT parsing failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("✗ Unexpected error during WebSocket auth: {}", e.getMessage(), e);
            return null;
        }
    }

    private Message<?> handleDisconnect(Message<?> message, String sessionId) {
        if (sessionId != null) {
            authenticationCache.remove(sessionId);
            log.debug("✓ Cleaned up auth cache for session: {}", sessionId);
        }
        return message;
    }

    private Message<?> attachCachedAuthentication(Message<?> message,
                                                  StompHeaderAccessor accessor,
                                                  String sessionId) {
        if (sessionId == null) {
            log.error("❌ null sessionId on non-CONNECT frame");
            return message;
        }

        UsernamePasswordAuthenticationToken auth = authenticationCache.get(sessionId);

        if (auth != null) {
            // FIX: setLeaveMutable BEFORE setUser, then rebuild
            accessor.setLeaveMutable(true);
            accessor.setUser(auth);
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("✅ Auth attached for sessionId={}, user={}", sessionId, auth.getName());
            return MessageBuilder.fromMessage(message).setHeaders(accessor).build();
        }

        // Fallback: re-validate from header (e.g. after server restart)
        log.warn("⚠️ No cached auth for session: {}, trying header fallback", sessionId);
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String username = jwtUtilService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtilService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken newAuth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    accessor.setLeaveMutable(true);
                    accessor.setUser(newAuth);
                    SecurityContextHolder.getContext().setAuthentication(newAuth);
                    authenticationCache.put(sessionId, newAuth);

                    log.info("✓ Recovered auth from header fallback: user={}", username);
                    return MessageBuilder.fromMessage(message).setHeaders(accessor).build();
                }
            } catch (Exception e) {
                log.warn("✗ Header fallback auth failed: {}", e.getMessage());
            }
        }

        log.error("❌ Could not authenticate message for sessionId: {}", sessionId);
        return message; // let it through; @MessageMapping will see null principal
    }

    private String extractBearerToken(String authHeader) {
        return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
    }
}