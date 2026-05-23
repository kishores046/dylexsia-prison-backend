package edu.ai.dyslexiaprisonbackend.controller;

import edu.ai.dyslexiaprisonbackend.dto.ml.MlRequestDto;
import edu.ai.dyslexiaprisonbackend.service.ml.MlIntegrationService;
import edu.ai.dyslexiaprisonbackend.service.ml.MlResultRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * FIX 7: Test ML Pipeline Endpoint
 * 
 * Allows testing the WebSocket delivery, feature aggregation, and ML integration
 * WITHOUT needing a full gaze session with 30+ frames.
 * 
 * **IMPORTANT**: This endpoint is only available in DEV profile.
 * It will NOT be available in production.
 * 
 * Usage:
 * ```bash
 * curl -X POST http://localhost:8090/api/test/trigger-ml \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer {jwt_token}" \
 *   -d '{
 *     "features": {
 *       "avgFixationDuration": 350.0,
 *       "maxFixationDuration": 500.0,
 *       "saccadeCount": 75.0,
 *       "regressionRate": 25.0,
 *       "readingSpeed": 120.0,
 *       "verticalStability": 45.0,
 *       "skippedWordRate": 20.0
 *     }
 *   }'
 * ```
 * 
 * Response:
 * ```json
 * {
 *   "message": "✓ Synthetic ML result triggered",
 *   "sessionId": "test-session-...",
 *   "riskScore": 85.59,
 *   "classification": "HIGH"
 * }
 * ```
 */
@RestController
@RequestMapping("/api/test")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "dev")
public class TestMLController {

    private final MlResultRoutingService resultRoutingService;
    private final MlIntegrationService mlIntegrationService;

    /**
     * Trigger a synthetic ML result for testing WebSocket delivery
     * 
     * @param request Map containing "features" with ML feature values to test with
     * @param auth Spring Security authentication (provides username)
     * @return Synthetic ML result
     */
    @PostMapping("/trigger-ml")
    public ResponseEntity<?> triggerSyntheticMlResult(
            @RequestBody Map<String, Object> request,
            Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("⚠️ Test ML endpoint called without authentication");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String username = auth.getName();
        log.info("→ Test ML trigger requested by: {}", username);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> features = (Map<String, Object>) request.get("features");
            
            if (features == null || features.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing 'features' field in request body"));
            }

            // Generate synthetic session ID for testing
            String testSessionId = "test-session-" + UUID.randomUUID();
            
            // Validate all 7 required features
            String[] requiredFeatures = {
                    "avgFixationDuration", "maxFixationDuration", "saccadeCount",
                    "regressionRate", "readingSpeed", "verticalStability", "skippedWordRate"
            };
            
            for (String feature : requiredFeatures) {
                if (!features.containsKey(feature)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Missing required feature: " + feature));
                }
            }

            // Convert features to Double values for ML request
            Map<String, Double> doubleFeatures = features.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                Object val = e.getValue();
                                if (val instanceof Number) {
                                    return ((Number) val).doubleValue();
                                }
                                return Double.parseDouble(val.toString());
                            }
                    ));

            // Build synthetic ML request (bypasses buffer/aggregation)
            MlRequestDto mlRequest = MlRequestDto.builder()
                    .sessionId(testSessionId)
                    .username(username)
                    .taskId("test-task")
                    .features(doubleFeatures)
                    .metadata(Map.of("testMode", true))
                    .build();

            log.info("→ Sending synthetic ML request: sessionId={}, features={}", 
                    testSessionId, doubleFeatures.keySet());

            // Route the test ML request through the normal ML service
            // This tests the full pipeline without needing a real gaze session
            mlIntegrationService.analyzeSessionAsync(mlRequest)
                    .thenAccept(mlResult -> {
                        if (mlResult != null && mlResult.isValid()) {
                            boolean routed = resultRoutingService.routeResultToUser(username, mlResult);
                            if (routed) {
                                log.info("✓ Test ML result routed to user: {} | riskScore={}, classification={}",
                                        username, mlResult.getRiskScore(), mlResult.getClassification());
                            } else {
                                log.warn("⚠️ Test ML result routing failed for user: {}", username);
                            }
                        } else {
                            log.warn("✗ Invalid ML result for test session");
                            resultRoutingService.sendErrorToUser(username, 
                                    "Test ML result was invalid");
                        }
                    })
                    .exceptionally(error -> {
                        log.error("✗ Test ML pipeline error: {}", error.getMessage(), error);
                        resultRoutingService.sendErrorToUser(username,
                                "Test ML analysis failed: " + error.getMessage());
                        return null;
                    });

            // Return immediately — actual result delivery is async via WebSocket
            return ResponseEntity.ok(Map.of(
                    "message", "✓ Synthetic ML result triggered",
                    "sessionId", testSessionId,
                    "username", username,
                    "note", "Result will be delivered asynchronously via WebSocket to /user/queue/result"
            ));

        } catch (Exception e) {
            log.error("✗ Error in test ML endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Test ML trigger failed: " + e.getMessage()));
        }
    }

    /**
     * Quick health check to verify test endpoint is available
     */
    @GetMapping("/health")
    public ResponseEntity<?> testEndpointHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "✓ Test endpoints available",
                "profile", "dev",
                "endpoints", Map.of(
                        "post", "/api/test/trigger-ml",
                        "description", "Trigger synthetic ML result for WebSocket testing"
                )
        ));
    }
}

