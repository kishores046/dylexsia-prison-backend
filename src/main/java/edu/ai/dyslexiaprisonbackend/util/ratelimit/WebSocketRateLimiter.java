package edu.ai.dyslexiaprisonbackend.util.ratelimit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket Rate Limiter Service
 * Prevents frame flooding per user
 * Uses Guava RateLimiter with per-user limits
 * Default: 100 frames per second per user
 * Can be configured based on eyetracking device specs
 * Production notes:
 * - Cache entries expire after 1 hour of inactivity (cleanup)
 * - Each user has independent rate limiting
 * - Suitable for single-instance; for distributed use Redis
 */
@Service
@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class WebSocketRateLimiter {
    
    private static final double DEFAULT_PERMITS_PER_SECOND = 100.0;  // frames/sec
    private static final double FEATURE_PERMITS_PER_SECOND = 10.0;    // features/sec (less frequent)
    
    private final LoadingCache<String, RateLimiter> frameRateLimiters = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, RateLimiter>() {
                @Override
                public @NonNull RateLimiter load(@NonNull String username) {
                    return RateLimiter.create(DEFAULT_PERMITS_PER_SECOND);
                }
            });
    
    private final LoadingCache<String, RateLimiter> featureRateLimiters = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, RateLimiter>() {
                @Override
                public @NonNull RateLimiter load(@NonNull String username) {
                    return RateLimiter.create(FEATURE_PERMITS_PER_SECOND);
                }
            });

    /**
     * Check if user can send a gaze frame
     * 
     * @param username authenticated user
     * @return true if within rate limit, false if rate limited
     */
    public boolean allowFrameMessage(String username) {
        try {
            RateLimiter limiter = frameRateLimiters.get(username);
            return limiter.tryAcquire();
        } catch (ExecutionException e) {
            log.error("Error acquiring frame rate limiter for user {}: {}", username, e.getMessage());
            return false;  // fail closed - reject on error
        }
    }

    /**
     * Check if user can send a gaze feature
     * Features are less frequent, so higher limit
     * 
     * @param username authenticated user
     * @return true if within rate limit, false if rate limited
     */
    public boolean allowFeatureMessage(String username) {
        try {
            RateLimiter limiter = featureRateLimiters.get(username);
            return limiter.tryAcquire();
        } catch (ExecutionException e) {
            log.error("Error acquiring feature rate limiter for user {}: {}", username, e.getMessage());
            return false;  // fail closed
        }
    }

    /**
     * For testing - set custom frame rate limit
     */
    public void setFrameRateLimit(String username, double permitsPerSecond) {
        try {
            RateLimiter limiter = frameRateLimiters.get(username);
            limiter.setRate(permitsPerSecond);
        } catch (ExecutionException e) {
            log.error("Error setting frame rate limit for user {}: {}", username, e.getMessage());
        }
    }

    /**
     * For testing - set custom feature rate limit
     */
    public void setFeatureRateLimit(String username, double permitsPerSecond) {
        try {
            RateLimiter limiter = featureRateLimiters.get(username);
            limiter.setRate(permitsPerSecond);
        } catch (ExecutionException e) {
            log.error("Error setting feature rate limit for user {}: {}", username, e.getMessage());
        }
    }
}

