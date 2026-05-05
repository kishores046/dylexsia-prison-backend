package edu.ai.dyslexiaprisonbackend.service.ml;

import edu.ai.dyslexiaprisonbackend.dto.gaze.FeaturePayloadDto;
import edu.ai.dyslexiaprisonbackend.dto.gaze.GazeFrameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GazeFeatureAggregator — computes the 7 clinical features defined in the paper.
 *
 * Paper spec (all thresholds from Table 1):
 *   f1  avgFixationDuration   > 300ms      (normal 200-250ms)
 *   f2  regressionRate        > 20%        (normal 10-15%)
 *   f3  saccadeCount          > 50/session (normal 30-40)
 *   f4  readingSpeed          < 150 WPM    (normal 200-300)
 *   f5  verticalStability     > 30px σ     (normal < 15px)
 *   f6  maxFixationDuration   > 450ms      (normal < 320ms)
 *   f7  skippedWordRate       > 15%        (normal < 5%)
 *
 * Gaze coordinate system: normalized 0.0-1.0 (from MediaPipe FaceMesh).
 * Screen dimensions come from session metadata to denormalize to pixels.
 *
 * Fixation definition (paper §III-B):
 *   Gaze stays within 50px radius for > 100ms consecutively.
 *
 * Saccade definition (paper §III-B):
 *   Eye movement jump > 50px between consecutive frames.
 */
@Service
@Slf4j
public class GazeFeatureAggregator {

    // Paper-defined constants (§III-B)
    private static final double FIXATION_RADIUS_PX      = 50.0;  // px
    private static final long   FIXATION_MIN_DURATION_MS = 100L; // ms
    private static final double SACCADE_THRESHOLD_PX    = 50.0;  // px
    private static final double REGRESSION_THRESHOLD_PX = 50.0;  // px — leftward jump > this
    private static final int    STORY_WORD_COUNT        = 175;   // paper: 150-200 words, use midpoint
    private static final int    DEFAULT_SCREEN_WIDTH    = 720;
    private static final int    DEFAULT_SCREEN_HEIGHT   = 600;

    public Map<String, Double> aggregate(List<GazeFrameDto> frames,
                                         List<FeaturePayloadDto> features,
                                         Map<String, Object> metadata) {

        if ((frames == null || frames.isEmpty()) &&
                (features == null || features.isEmpty())) {
            log.warn("⚠️ No gaze data to aggregate — returning defaults");
            return getDefaultFeatures();
        }

        // Extract screen dimensions from session metadata for denormalization
        int screenWidth  = extractInt(metadata, "screenWidth",  DEFAULT_SCREEN_WIDTH);
        int screenHeight = extractInt(metadata, "screenHeight", DEFAULT_SCREEN_HEIGHT);

        log.debug("Aggregating {} frames, screen={}x{}",
                frames != null ? frames.size() : 0, screenWidth, screenHeight);

        Map<String, Double> result = new HashMap<>();

        if (frames != null && !frames.isEmpty()) {
            // Detect fixations first — needed for f1, f6
            List<Fixation> fixations = detectFixations(frames, screenWidth, screenHeight);

            result.put("avgFixationDuration", computeAvgFixationDuration(fixations));
            result.put("maxFixationDuration", computeMaxFixationDuration(fixations));
            result.put("saccadeCount",        (double) computeSaccadeCount(frames, screenWidth, screenHeight));
            result.put("regressionRate",      computeRegressionRate(frames, screenWidth));
            result.put("readingSpeed",        computeReadingSpeed(frames));
            result.put("verticalStability",   computeVerticalStability(frames, screenHeight));
            result.put("skippedWordRate",     computeSkippedWordRate(frames, screenWidth));
        }

        // If FeaturePayload fixation events exist (sent explicitly by frontend),
        // they are more accurate — override fixation fields
        if (features != null && !features.isEmpty()) {
            double featureAvg = computeAvgFixationFromPayload(features);
            double featureMax = computeMaxFixationFromPayload(features);
            if (featureAvg > 0) result.put("avgFixationDuration", featureAvg);
            if (featureMax > 0) result.put("maxFixationDuration", featureMax);
        }

        log.info("✓ Aggregated features: {}", result);
        return result;
    }

    // Keep backward compat for callers that don't pass metadata
    public Map<String, Double> aggregate(List<GazeFrameDto> frames,
                                         List<FeaturePayloadDto> features) {
        return aggregate(frames, features, Collections.emptyMap());
    }

    // -------------------------------------------------------------------------
    // Fixation detection (paper §III-B: 50px radius, >100ms)
    // -------------------------------------------------------------------------

    private List<Fixation> detectFixations(List<GazeFrameDto> frames,
                                           int screenWidth, int screenHeight) {
        List<Fixation> fixations = new ArrayList<>();
        if (frames.size() < 2) return fixations;

        int start = 0;
        while (start < frames.size()) {
            GazeFrameDto anchor = frames.get(start);
            if (anchor.getGazeX() == null || anchor.getGazeY() == null
                    || anchor.getTimestamp() == null) {
                start++;
                continue;
            }

            double anchorXpx = anchor.getGazeX() * screenWidth;
            double anchorYpx = anchor.getGazeY() * screenHeight;
            int end = start;

            // Extend fixation cluster while within FIXATION_RADIUS_PX
            for (int i = start + 1; i < frames.size(); i++) {
                GazeFrameDto f = frames.get(i);
                if (f.getGazeX() == null || f.getGazeY() == null) break;
                double xpx = f.getGazeX() * screenWidth;
                double ypx = f.getGazeY() * screenHeight;
                double dist = Math.sqrt(Math.pow(xpx - anchorXpx, 2) +
                        Math.pow(ypx - anchorYpx, 2));
                if (dist <= FIXATION_RADIUS_PX) {
                    end = i;
                } else {
                    break;
                }
            }

            // Check minimum duration
            Long t0 = frames.get(start).getTimestamp();
            Long t1 = frames.get(end).getTimestamp();
            if (t0 != null && t1 != null) {
                long duration = t1 - t0;
                if (duration >= FIXATION_MIN_DURATION_MS) {
                    fixations.add(new Fixation(anchorXpx, anchorYpx, duration));
                }
            }

            start = end + 1;
        }

        log.debug("Detected {} fixations from {} frames", fixations.size(), frames.size());
        return fixations;
    }

    // -------------------------------------------------------------------------
    // f1: Average fixation duration (ms)
    // -------------------------------------------------------------------------
    private double computeAvgFixationDuration(List<Fixation> fixations) {
        if (fixations.isEmpty()) return 200.0; // paper normal range midpoint
        return fixations.stream()
                .mapToLong(f -> f.durationMs)
                .average()
                .orElse(200.0);
    }

    // -------------------------------------------------------------------------
    // f6: Max fixation duration (ms)
    // -------------------------------------------------------------------------
    private double computeMaxFixationDuration(List<Fixation> fixations) {
        if (fixations.isEmpty()) return 300.0;
        return fixations.stream()
                .mapToLong(f -> f.durationMs)
                .max()
                .orElse(300L);
    }

    // -------------------------------------------------------------------------
    // f3: Saccade count — jumps > 50px between consecutive frames (paper §III-B)
    // -------------------------------------------------------------------------
    private int computeSaccadeCount(List<GazeFrameDto> frames,
                                    int screenWidth, int screenHeight) {
        int saccades = 0;
        for (int i = 1; i < frames.size(); i++) {
            Double px = frames.get(i - 1).getGazeX();
            Double py = frames.get(i - 1).getGazeY();
            Double cx = frames.get(i).getGazeX();
            Double cy = frames.get(i).getGazeY();
            if (px == null || py == null || cx == null || cy == null) continue;

            double dxPx = (cx - px) * screenWidth;
            double dyPx = (cy - py) * screenHeight;
            double dist = Math.sqrt(dxPx * dxPx + dyPx * dyPx);

            if (dist > SACCADE_THRESHOLD_PX) saccades++;
        }
        return saccades;
    }

    // -------------------------------------------------------------------------
    // f2: Regression rate — leftward saccades > 50px / total transitions × 100
    // -------------------------------------------------------------------------
    private double computeRegressionRate(List<GazeFrameDto> frames, int screenWidth) {
        if (frames.size() < 2) return 0.0;
        long regressions = 0;
        int transitions = 0;
        for (int i = 1; i < frames.size(); i++) {
            Double prevX = frames.get(i - 1).getGazeX();
            Double currX = frames.get(i).getGazeX();
            if (prevX == null || currX == null) continue;
            transitions++;
            // Regression = significant leftward jump (paper: right-to-left saccade)
            double dxPx = (currX - prevX) * screenWidth;
            if (dxPx < -REGRESSION_THRESHOLD_PX) regressions++;
        }
        if (transitions == 0) return 0.0;
        return (regressions * 100.0) / transitions;
    }

    // -------------------------------------------------------------------------
    // f4: Reading speed (WPM)
    // Paper: 150-200 word story, 60s session → normal reader ≈ 200-300 WPM
    // Use actual session duration; clamp to [0, 600] WPM (human range)
    // -------------------------------------------------------------------------
    private double computeReadingSpeed(List<GazeFrameDto> frames) {
        if (frames.size() < 2) return 0.0;
        long durationMs = computeFrameDuration(frames);

        // Paper: 60-second session over a 150-200 word story.
        // If we have less than 20 seconds of data we can't make a meaningful WPM estimate —
        // return 0 and let the rule engine treat it as "no data" rather than falsely
        // reporting 600 WPM which always triggers the readingSpeed clamp.
        if (durationMs < 20_000) {
            log.debug("⏭ readingSpeed: only {}ms of data — need 20s+ for WPM estimate, returning 0",
                    durationMs);
            return 0.0;
        }

        double durationMinutes = durationMs / 60_000.0;
        double wpm = STORY_WORD_COUNT / durationMinutes;
        return Math.min(600.0, Math.max(0.0, wpm));
    }

    // -------------------------------------------------------------------------
    // f5: Vertical stability — σ of y in PIXELS (paper threshold: > 30px)
    // Must denormalize from 0-1 to pixels
    // -------------------------------------------------------------------------
    private double computeVerticalStability(List<GazeFrameDto> frames, int screenHeight) {
        double[] yPx = frames.stream()
                .filter(f -> f.getGazeY() != null)
                .mapToDouble(f -> f.getGazeY() * screenHeight)  // denormalize to px
                .toArray();
        if (yPx.length < 2) return 0.0;
        return Math.sqrt(computeVariance(yPx));  // standard deviation in pixels
    }

    // -------------------------------------------------------------------------
    // f7: Skipped word rate — large rightward jumps > 20% of screen width
    // Heuristic: jump > 0.20 normalized = skipping ~1 word
    // -------------------------------------------------------------------------
    private double computeSkippedWordRate(List<GazeFrameDto> frames, int screenWidth) {
        if (frames.size() < 2) return 0.0;
        long skips = 0;
        int transitions = 0;
        for (int i = 1; i < frames.size(); i++) {
            Double prevX = frames.get(i - 1).getGazeX();
            Double currX = frames.get(i).getGazeX();
            if (prevX == null || currX == null) continue;
            transitions++;
            double dxNorm = currX - prevX;
            // Large forward jump = word skipped
            if (dxNorm > 0.20) skips++;
        }
        if (transitions == 0) return 0.0;
        return (skips * 100.0) / transitions;
    }

    // -------------------------------------------------------------------------
    // FeaturePayload overrides (if frontend sends explicit fixation events)
    // -------------------------------------------------------------------------
    private double computeAvgFixationFromPayload(List<FeaturePayloadDto> features) {
        return features.stream()
                .filter(f -> "fixation".equalsIgnoreCase(f.getFeatureType()))
                .filter(f -> f.getDuration() != null && f.getDuration() > 0)
                .mapToLong(FeaturePayloadDto::getDuration)
                .average().orElse(0.0);
    }

    private double computeMaxFixationFromPayload(List<FeaturePayloadDto> features) {
        return features.stream()
                .filter(f -> "fixation".equalsIgnoreCase(f.getFeatureType()))
                .filter(f -> f.getDuration() != null && f.getDuration() > 0)
                .mapToLong(FeaturePayloadDto::getDuration)
                .max().orElse(0L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private long computeFrameDuration(List<GazeFrameDto> frames) {
        if (frames.size() < 2) return 0;
        Long first = frames.get(0).getTimestamp();
        Long last  = frames.get(frames.size() - 1).getTimestamp();
        if (first == null || last == null) return 0;
        return last - first;
    }

    private double computeVariance(double[] values) {
        if (values.length < 2) return 0.0;
        double mean = Arrays.stream(values).average().orElse(0.0);
        return Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .sum() / values.length;
    }

    private int extractInt(Map<String, Object> metadata, String key, int defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) return defaultValue;
        Object val = metadata.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return defaultValue; }
    }

    private Map<String, Double> getDefaultFeatures() {
        Map<String, Double> d = new HashMap<>();
        d.put("avgFixationDuration", 225.0);  // paper normal midpoint
        d.put("maxFixationDuration", 300.0);
        d.put("saccadeCount",        35.0);   // paper normal midpoint
        d.put("regressionRate",      12.0);   // paper normal midpoint
        d.put("readingSpeed",        250.0);  // paper normal midpoint
        d.put("verticalStability",   12.0);   // paper normal midpoint (px)
        d.put("skippedWordRate",     3.0);
        return d;
    }

    // -------------------------------------------------------------------------
    // Internal fixation record
    // -------------------------------------------------------------------------
    private static class Fixation {
        final double xPx, yPx;
        final long durationMs;
        Fixation(double xPx, double yPx, long durationMs) {
            this.xPx = xPx; this.yPx = yPx; this.durationMs = durationMs;
        }
    }


}