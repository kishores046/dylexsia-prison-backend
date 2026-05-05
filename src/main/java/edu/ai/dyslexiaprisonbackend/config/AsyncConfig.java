package edu.ai.dyslexiaprisonbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Async Configuration & HTTP Client Setup
 * 
 * Enables:
 * - @Async for non-blocking method execution
 * - WebClient bean for reactive HTTP calls to ML service
 * 
 * WebClient uses:
 * - Netty for async networking
 * - Jackson for JSON serialization
 * - Built-in timeout/retry support
 */
@EnableAsync
@Configuration
public class AsyncConfig {
    
    /**
     * Configure WebClient for ML service communication
     * 
     * Uses reactive stack (Project Reactor) for non-blocking I/O
     * This prevents buffer flush from blocking WebSocket handlers
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .build();
    }
}
