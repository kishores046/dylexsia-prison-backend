package edu.ai.dyslexiaprisonbackend.config;

import edu.ai.dyslexiaprisonbackend.service.buffer.SessionBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled Buffer Flusher - Periodic maintenance of gaze buffers
 * 
 * Responsibilities:
 * - Flush stale buffers based on time (every 2+ seconds)
 * - Log buffer health metrics
 * - Prevent memory buildup from slow consumers
 * 
 * Schedule:
 * - Every 500ms: Check for stale buffers
 * - Every 30s: Log metrics
 * - Every 5m: Collect stats
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledBufferFlusher {
    
    private final SessionBufferService sessionBufferService;
    
    /**
     * Periodic flush of stale buffers
     * Triggered every 500ms to flush buffers that exceed 2-second threshold
     */
    @Scheduled(fixedRate = 500, initialDelay = 2000)
    public void flushStaleBuffers() {
        try {
            sessionBufferService.flushStaleBuffers();
        } catch (Exception e) {
            log.error("✗ Error during scheduled buffer flush", e);
        }
    }
    
    /**
     * Log buffer health metrics
     * Triggered every 30 seconds for monitoring
     */
    @Scheduled(fixedRate = 30000)
    public void logBufferMetrics() {
        try {
            int activeBuffers = sessionBufferService.getActiveBufferCount();
            int pendingFrames = sessionBufferService.getTotalPendingFrames();
            int pendingFeatures = sessionBufferService.getTotalPendingFeatures();
            
            log.info("📊 Buffer metrics: activeBuffers={}, pendingFrames={}, "
                    + "pendingFeatures={}",
                    activeBuffers, pendingFrames, pendingFeatures);
            
            if (activeBuffers > 0 && activeBuffers <= 10) {
                for (String stat : sessionBufferService.getAllBufferStats()) {
                    log.debug("  {}", stat);
                }
            }
            
        } catch (Exception e) {
            log.error("✗ Error logging buffer metrics", e);
        }
    }
}

