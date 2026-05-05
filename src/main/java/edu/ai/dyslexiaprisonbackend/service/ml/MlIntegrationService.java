package edu.ai.dyslexiaprisonbackend.service.ml;

import edu.ai.dyslexiaprisonbackend.dto.ml.MlRequestDto;
import edu.ai.dyslexiaprisonbackend.dto.ml.MlResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MlIntegrationService {

    private final WebClient webClient;

    @Value("${ml.service.url:http://localhost:8000/analyze}")
    private String mlServiceUrl;

    @Value("${ml.service.timeout:2000}")
    private long timeoutMs;

    @Value("${ml.service.retries:1}")
    private int maxRetries;

    public CompletableFuture<MlResultDto> analyzeSessionAsync(MlRequestDto request) {
        if (request == null || !request.isValid()) {
            log.warn("⚠️ Invalid ML request, skipping: {}", request);
            return CompletableFuture.completedFuture(
                    getSafeDefaultResult(request != null ? request.getSessionId() : "unknown"));
        }

        log.info("→ Sending to ML service: sessionId={}, features.count={}",
                request.getSessionId(),
                request.getFeatures() != null ? request.getFeatures().size() : 0);

        // FIX: log the serialized payload so you can compare against the FastAPI schema
        log.debug("→ ML request payload: {}", request);
        return webClient.post()
                .uri(mlServiceUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                // FIX: surface the 422 response body in the error so you can see what's wrong
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("<empty body>")
                                .flatMap(body -> {
                                    log.error("✗ ML service returned {}: {}",
                                            clientResponse.statusCode(), body);
                                    return Mono.error(new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            clientResponse.statusCode().toString(),
                                            null, body.getBytes(), null));
                                })
                )
                .bodyToMono(MlResultDto.class)
                .timeout(Duration.ofMillis(timeoutMs))
                // FIX: only retry on network/connection errors, NOT on 4xx/5xx responses.
                // Retrying a 422 is pointless — the same bad payload will always get the same rejection.
                .retryWhen(Retry.max(maxRetries)
                        .filter(throwable -> isRetryable(throwable)))
                .doOnSuccess(result -> logSuccess(result, request.getSessionId()))
                .doOnError(error -> logError(error, request.getSessionId()))
                .onErrorResume(error -> Mono.just(getSafeDefaultResult(request.getSessionId())))
                .toFuture();
    }

    /**
     * Only retry on transient network/connection errors.
     * Never retry on 4xx (client error — bad payload) or 5xx (server crash).
     * 422 in particular means the schema is wrong — retrying will always fail.
     */
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            // 429 Too Many Requests and 503 Service Unavailable are worth retrying.
            // 422 Unprocessable Content is NOT — the payload is the problem.
            return status == 429 || status == 503 || status == 502;
        }
        // Retry on pure network failures (connection refused, timeout, etc.)
        return throwable instanceof WebClientException
                && !(throwable instanceof WebClientResponseException);
    }

    private MlResultDto getSafeDefaultResult(String sessionId) {
        return MlResultDto.builder()
                .sessionId(sessionId)
                .riskScore(50.0)
                .classification("MODERATE")
                .confidence(0.0)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private void logSuccess(MlResultDto result, String sessionId) {
        if (result != null && result.isValid()) {
            log.info("✓ ML analysis complete: sessionId={}, riskScore={}, classification={}, confidence={}",
                    sessionId, result.getRiskScore(), result.getClassification(), result.getConfidence());
        } else {
            log.warn("✗ Invalid result from ML service for {}", sessionId);
        }
    }

    private void logError(Throwable error, String sessionId) {
        // Don't log full stack for 4xx — the onStatus handler already logged the body
        if (error instanceof WebClientResponseException ex && ex.getStatusCode().is4xxClientError()) {
            log.warn("✗ ML service client error for {}: {} (see above for response body)",
                    sessionId, ex.getStatusCode());
        } else {
            log.error("✗ ML service error for {}: {}", sessionId, error.getMessage(), error);
        }
    }
}