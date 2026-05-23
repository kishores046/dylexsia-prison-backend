package edu.ai.dyslexiaprisonbackend.service.buffer;

import edu.ai.dyslexiaprisonbackend.dto.gaze.FeaturePayloadDto;
import edu.ai.dyslexiaprisonbackend.dto.gaze.GazeFrameDto;
import edu.ai.dyslexiaprisonbackend.dto.ml.MlRequestDto;
import edu.ai.dyslexiaprisonbackend.service.ml.GazeFeatureAggregator;
import edu.ai.dyslexiaprisonbackend.service.ml.MlIntegrationService;
import edu.ai.dyslexiaprisonbackend.service.ml.MlResultRoutingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class GazeBatchProcessor {

    private final GazeFeatureAggregator featureAggregator;
    private final MlIntegrationService mlIntegrationService;
    private final MlResultRoutingService resultRoutingService;

    // Session-level frame/feature accumulators.
    // Paper requires ~300 frames over 60s — per-batch (5-7 frames) stats are meaningless.
    private final Map<String, List<GazeFrameDto>> sessionFrameAccumulator =
            new ConcurrentHashMap<>();
    private final Map<String, List<FeaturePayloadDto>> sessionFeatureAccumulator =
            new ConcurrentHashMap<>();

    // Tracks how many frames were accumulated when we last sent to ML.
    // Used to avoid hammering the ML service every 2s with near-identical features.
    private final Map<String, Integer> lastSentAtFrameCount = new ConcurrentHashMap<>();

    // FIX 1 & 2: Make thresholds configurable (tunable via application.properties)
    @Value("${ml.pipeline.min-frames-for-first-analysis:10}")
    private int minFramesForMl;

    @Value("${ml.pipeline.resend-every-n-new-frames:20}")
    private int resendEveryNNewFrames;

    // All 7 features the Python schema requires — validated before every ML call.
    private static final List<String> REQUIRED_FEATURES = List.of(
            "avgFixationDuration", "maxFixationDuration", "saccadeCount",
            "regressionRate", "readingSpeed", "verticalStability", "skippedWordRate");

    @Data
    @Builder
    @AllArgsConstructor
    public static class GazeBatch {
        private String sessionId;
        private String username;
        private String taskId;
        private List<GazeFrameDto> frames;
        private List<FeaturePayloadDto> features;
        private long batchTimestamp;
        private String batchId;
        private Map<String, Object> metadata;
        private String trigger;

        public int getSize() {
            return (frames != null ? frames.size() : 0)
                    + (features != null ? features.size() : 0);
        }

        public String getSummary() {
            return String.format(
                    "GazeBatch[id=%s, session=%s, user=%s, frames=%d, features=%d, trigger=%s]",
                    batchId, sessionId, username,
                    frames != null ? frames.size() : 0,
                    features != null ? features.size() : 0,
                    trigger);
        }
    }

    // Overload for callers that don't supply a trigger (e.g. periodic flush from SessionBufferService)
    public void processBatch(
            String sessionId,
            String username,
            String taskId,
            List<GazeFrameDto> frames,
            List<FeaturePayloadDto> features,
            Map<String, Object> sessionMetadata) {
        processBatch(sessionId, username, taskId, frames, features, sessionMetadata, "PERIODIC");
    }

    public void processBatch(
            String sessionId,
            String username,
            String taskId,
            List<GazeFrameDto> frames,
            List<FeaturePayloadDto> features,
            Map<String, Object> sessionMetadata,
            String trigger) {

        try {
            GazeBatch batch = GazeBatch.builder()
                    .sessionId(sessionId)
                    .username(username)
                    .taskId(taskId)
                    .frames(frames != null ? frames : new ArrayList<>())
                    .features(features != null ? features : new ArrayList<>())
                    .batchTimestamp(System.currentTimeMillis())
                    .batchId(generateBatchId(sessionId))
                    .metadata(sessionMetadata != null ? sessionMetadata : new HashMap<>())
                    .trigger(trigger)
                    .build();

            log.info("→ Processing batch: {}", batch.getSummary());

            if (!isValidBatch(batch)) {
                log.warn("✗ Invalid batch — skipping: {}", batch.getSummary());
                return;
            }

            // --- STEP 1: Accumulate frames across batches ---
            // Each 2-second periodic batch has 5-7 frames — far too few for meaningful
            // fixation detection or WPM calculation. Accumulate until we have enough.
            List<GazeFrameDto> allFrames = sessionFrameAccumulator
                    .computeIfAbsent(sessionId, k -> new ArrayList<>());
            List<FeaturePayloadDto> allFeatures = sessionFeatureAccumulator
                    .computeIfAbsent(sessionId, k -> new ArrayList<>());

            allFrames.addAll(batch.getFrames());
            allFeatures.addAll(batch.getFeatures());

            int totalFrames = allFrames.size();
            boolean isSessionEnd = "SESSION_END".equalsIgnoreCase(trigger);

            // --- STEP 2: Decide whether to call ML service ---
             // Rules (in priority order):
             //   a) Always send on SESSION_END regardless of frame count
             //   b) Skip until we have MIN_FRAMES_FOR_ML frames (first meaningful window)
             //   c) After first send, only resend every RESEND_EVERY_N_NEW_FRAMES new frames
             //      to avoid sending near-identical features every 2 seconds
             if (!isSessionEnd) {
                 int lastSentAt = lastSentAtFrameCount.getOrDefault(sessionId, 0);
                 boolean firstSend = (lastSentAt == 0);
                 boolean enoughForFirstSend = (totalFrames >= minFramesForMl);
                 boolean enoughNewFramesSinceLastSend =
                         (totalFrames - lastSentAt) >= resendEveryNNewFrames;

                 if (firstSend && !enoughForFirstSend) {
                     log.debug("⏭ Skipping ML — only {} frames accumulated, need {}+ for first send",
                             totalFrames, minFramesForMl);
                     return;
                 }

                 if (!firstSend && !enoughNewFramesSinceLastSend) {
                     log.debug("⏭ Skipping ML — {} total frames, only {} new since last send "
                                     + "(need {}+ new frames between sends)",
                             totalFrames,
                             totalFrames - lastSentAt,
                             resendEveryNNewFrames);
                     return;
                 }
             }

            // --- STEP 3: Aggregate features over ALL accumulated frames ---
            log.info("✓ Aggregating {} accumulated frames, {} features for session {}",
                    totalFrames, allFeatures.size(), sessionId);

            Map<String, Double> aggregatedFeatures = featureAggregator.aggregate(
                    new ArrayList<>(allFrames),    // defensive copy — aggregator must not modify
                    new ArrayList<>(allFeatures),
                    batch.getMetadata()
            );

            // --- STEP 4: Validate all 7 required features are present ---
             // Catches aggregator bugs before they reach the ML service (avoids 422).
             List<String> missing = REQUIRED_FEATURES.stream()
                     .filter(k -> !aggregatedFeatures.containsKey(k))
                     .toList();
             if (!missing.isEmpty()) {
                 // FIX 3: Send specific error to frontend so user knows why ML didn't run
                 log.error("✗ Aggregated features missing required keys: {} — reporting to user",
                         missing);
                 resultRoutingService.sendErrorToUser(username, 
                         "ML feature extraction failed — missing fields: " + String.join(", ", missing));
                 return;
             }

            log.info("✓ Features ready: {}", aggregatedFeatures);

            // --- STEP 5: Build ML request ---
            MlRequestDto mlRequest = MlRequestDto.builder()
                    .sessionId(batch.getSessionId())
                    .username(batch.getUsername())
                    .taskId(batch.getTaskId())
                    .features(aggregatedFeatures)
                    .metadata(batch.getMetadata())
                    .build();

            // --- STEP 6: Record that we're sending now, before the async call ---
             // Must happen before the async call so concurrent batches for the same session
             // don't both pass the frame-count check and double-send.
             lastSentAtFrameCount.put(sessionId, totalFrames);

             // --- STEP 7: Send to ML service asynchronously ---
             // FIX 4: CRITICAL — Move accumulator cleanup to AFTER the async call completes,
             // NOT before. If SESSION_END and ML response takes 500ms, the session state
             // must still exist when the result finally arrives.
             mlIntegrationService.analyzeSessionAsync(mlRequest)
                     .thenAccept(mlResult -> {
                         try {
                             if (mlResult == null) {
                                 log.warn("✗ Null ML result for session: {}", sessionId);
                                 return;
                             }
                             if (!mlResult.isValid()) {
                                 log.warn("✗ Invalid ML result for session: {} — result: {}",
                                         sessionId, mlResult);
                                 return;
                             }
                             boolean routed = resultRoutingService.routeResultToUser(
                                     batch.getUsername(), mlResult);
                             if (routed) {
                                 log.info("✓ ML result routed to user: {} | riskScore={}, classification={}",
                                         batch.getUsername(),
                                         mlResult.getRiskScore(),
                                         mlResult.getClassification());
                             } else {
                                 log.warn("⚠️ ML result routing failed for user: {}", batch.getUsername());
                             }
                         } finally {
                             // FIX 4: Clean up accumulators AFTER result successfully routed,
                             // not before. This ensures SESSION_END result doesn't race cleanup.
                             if (isSessionEnd) {
                                 sessionFrameAccumulator.remove(sessionId);
                                 sessionFeatureAccumulator.remove(sessionId);
                                 lastSentAtFrameCount.remove(sessionId);
                                 log.info("✓ Session state cleaned up for: {}", sessionId);
                             }
                         }
                     })
                     .exceptionally(error -> {
                         try {
                             log.error("✗ ML pipeline error for session {}: {}",
                                     sessionId, error.getMessage(), error);
                             resultRoutingService.sendErrorToUser(
                                     username, "ML analysis failed: " + error.getMessage());
                         } finally {
                             // FIX 4: Also clean up on error if SESSION_END
                             if (isSessionEnd) {
                                 sessionFrameAccumulator.remove(sessionId);
                                 sessionFeatureAccumulator.remove(sessionId);
                                 lastSentAtFrameCount.remove(sessionId);
                                 log.info("✓ Session state cleaned up for: {} (after error)", sessionId);
                             }
                         }
                         return null;
                     });

        } catch (Exception e) {
            log.error("✗ Error processing batch for session: {}", sessionId, e);
        }
    }

    private boolean isValidBatch(GazeBatch batch) {
        if (batch.getSessionId() == null || batch.getSessionId().isBlank()) {
            log.warn("⚠️ Batch missing sessionId");
            return false;
        }
        if (batch.getUsername() == null || batch.getUsername().isBlank()) {
            log.warn("⚠️ Batch missing username");
            return false;
        }
        if (batch.getSize() == 0) {
            log.warn("⚠️ Batch is empty");
            return false;
        }
        return true;
    }

    private String generateBatchId(String sessionId) {
        long timestamp = System.currentTimeMillis();
        int hash = Objects.hash(sessionId, timestamp);
        return String.format("%s-%d-%x", sessionId, timestamp, hash);
    }
}