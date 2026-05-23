package edu.ai.dyslexiaprisonbackend.config;

import org.springframework.context.annotation.Configuration;

/**
 * ActuatorConfig - Configure Spring Boot Actuator for observability
 * 
 * Exposes endpoints:
 * - /actuator/health - application health status
 * - /actuator/prometheus - Prometheus metrics
 * - /actuator/metrics - available metrics
 * - /actuator/metrics/{metric.name} - specific metric
 * 
 * Configuration is defined in application.yml (management.* properties)
 */
@Configuration
public class ActuatorConfig {
    // Actuator configuration is in application.yml
}

