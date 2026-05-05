package edu.ai.dyslexiaprisonbackend.config;

import edu.ai.dyslexiaprisonbackend.security.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for STOMP over WebSocket
 * Handles:
 * - STOMP endpoint registration (/ws/gaze)
 * - Message broker configuration (topics, queues)
 * - SockJS fallback for older browsers
 * - Channel interceptor registration for JWT auth
 * Production considerations:
 * - Simple broker suitable for single-instance (consider RabbitMQ for distributed)
 * - User destination prefix enables per-user queues (/user/queue/*)
 * - Application destination prefix routes @MessageMapping messages (/app/*)
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * Configure the message broker
     * 
     * - /topic/* : for broadcast messages (all subscribers)
     * - /queue/* : for point-to-point messages (single user)
     * - /app/* : server-side @MessageMapping endpoints
     * - /user/* : user-specific destination prefix
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        
        log.info("✓ Message broker configured - simple broker enabled for /topic and /queue");
    }

    /**
     * Register STOMP endpoint with SockJS fallback
     * 
     * FIX: Changed from wildcard "*" to explicit origins
     * This prevents CORS vulnerabilities with credential-based connections
     * 
     * SockJS provides fallback transports:
     * - WebSocket (primary)
     * - HTTP Long Polling
     * - HTTP Streaming
     * - IFrame-based transports
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws/gaze")
                .setAllowedOrigins(
                        "http://localhost:8080",
                        "http://127.0.0.1:8080",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                )
                .withSockJS()
            .setHeartbeatTime(25000)    // 25s heartbeat
            .setDisconnectDelay(5000);  // 5s disconnect delay
        
        log.info("✓ STOMP endpoint registered at /ws/gaze with SockJS fallback");
    }

    /**
     * Configure channel interceptor for JWT authentication
     * Intercepts all incoming messages on the WebSocket channel
     * to extract and validate JWT tokens before connection/message processing
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        log.info("✓ WebSocket auth interceptor configured for client inbound channel");
    }
}

