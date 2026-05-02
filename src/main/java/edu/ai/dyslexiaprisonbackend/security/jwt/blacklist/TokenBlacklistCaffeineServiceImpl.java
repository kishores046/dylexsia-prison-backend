package edu.ai.dyslexiaprisonbackend.security.jwt.blacklist;


import com.github.benmanes.caffeine.cache.Cache;
import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import io.jsonwebtoken.Claims;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistCaffeineServiceImpl implements TokenBlacklistService{

    private final Cache<@NonNull String, Boolean> tokenBlacklistCache;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtUtilService jwtUtil;

    /**
     * Blacklist a token (stores in both cache and DB)
     */
    @Transactional
    public void blacklistToken(String token, String userEmail) {
        try {

            String tokenId = jwtUtil.extractTokenId(token);

            tokenBlacklistCache.put(tokenId, Boolean.TRUE);

            Date expiration = jwtUtil.extractExpiration(token);

            LocalDateTime expiresAt = expiration.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            BlacklistedToken entity =
                    new BlacklistedToken(tokenId, expiresAt, userEmail);

            blacklistedTokenRepository.save(entity);

            log.info("Token blacklisted for user: {}", userEmail);

            log.info("Token blacklisted for user: {}. Cache size: {}",
                    userEmail, tokenBlacklistCache.estimatedSize());
        } catch (Exception e) {
            log.error("Error blacklisting token", e);
            throw new RuntimeException("Blacklist failed", e);
        }
    }

    /**
     * Check if token is blacklisted (checks cache first, then DB)
     */
    public boolean isBlacklisted(String token) {
        String tokenId = jwtUtil.extractTokenId(token);

        Boolean cached = tokenBlacklistCache.getIfPresent(tokenId);

        if (cached != null && cached) {
            return true;
        }

        boolean exists =
                blacklistedTokenRepository.existsByTokenId(tokenId);

        if (exists) {
            tokenBlacklistCache.put(tokenId, Boolean.TRUE);
        }

        return exists;
    }

    /**
     * Clean up expired tokens from database every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        blacklistedTokenRepository.deleteExpiredTokens(now);
        log.info("Cleaned up expired blacklisted tokens");
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format(
                "Cache Stats - Size: %d, DB Count: %d, Hits: %d, Misses: %d",
                tokenBlacklistCache.estimatedSize(),
                blacklistedTokenRepository.count(),
                tokenBlacklistCache.stats().hitCount(),
                tokenBlacklistCache.stats().missCount()
        );
    }
}