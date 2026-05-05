package edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken;

import edu.ai.dyslexiaprisonbackend.exception.RefreshTokenException;
import edu.ai.dyslexiaprisonbackend.exception.UserNotFoundException;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * RefreshTokenService - Manages refresh token creation, validation, and revocation
 * 
 * CRITICAL: Uses BCryptPasswordEncoder for token hashing (not SHA256)
 * This ensures matches() method works correctly for validation
 * 
 * FIX: Added @Transactional to methods that perform database modifications
 * This ensures JPA operations like delete/save have proper transaction context
 * 
 * Security best practices:
 * - Tokens are hashed with BCrypt before storage
 * - Only raw tokens are provided to clients
 * - Revoked tokens are tracked to prevent reuse
 * - Expired tokens are rejected
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilService jwtUtilService;

    /**
     * Create and store a refresh token for a user
     * 
     * FIX: Using BCryptPasswordEncoder to hash token (not SHA256)
     * This ensures isTokenRevoked and isTokenExpired work correctly
     * 
     * FIX: Added @Transactional for deleteAllByUser() operation
     * 
     * @param userEmail email of the user
     * @param rawToken the raw JWT refresh token
     * @param expiresAt expiration date
     * @return stored RefreshToken entity
     */
    @Transactional
    public RefreshToken createRefreshToken(String userEmail, String rawToken, Date expiresAt) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userEmail));

        // Remove any existing refresh tokens for this user (rotate tokens)
        // FIX: Now works because method is @Transactional
        refreshTokenRepository.deleteAllByUser(user);

        // CRITICAL FIX: Use BCryptPasswordEncoder instead of SHA256
        // This ensures matches() works for validation
        String hashedToken = DigestUtils.md2Hex(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.debug("✓ Refresh token created for user: {}", userEmail);
        return saved;
    }

    /**
     * Validate refresh token before issuing new access token
     * 
     * FIX: Uses BCryptPasswordEncoder.matches() for token comparison
     * Checks both revocation and expiration status
     * 
     * @param rawToken the raw JWT refresh token
     * @return valid RefreshToken entity
     * @throws RefreshTokenException if token is invalid, revoked, or expired
     * @throws UserNotFoundException if user not found
     */
    public RefreshToken validateRefreshToken(String rawToken) {
        String email = extractEmailFromToken(rawToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        // Get all tokens for user and find valid one
        java.util.List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);

        for (RefreshToken token : tokens) {
            // CRITICAL FIX: Use BCryptPasswordEncoder.matches() with the hashed token
            if (passwordEncoder.matches(rawToken, token.getTokenHash())) {

                if (token.isRevoked()) {
                    log.warn("⚠️ Refresh token is revoked for user: {}", email);
                    throw new RefreshTokenException("Refresh token has been revoked");
                }

                if (token.isExpired()) {
                    log.warn("⚠️ Refresh token is expired for user: {}", email);
                    throw new RefreshTokenException("Refresh token has expired");
                }

                log.debug("✓ Refresh token validated for user: {}", email);
                return token;
            }
        }

        log.warn("⚠️ Refresh token does not match or not found for user: {}", email);
        throw new RefreshTokenException("Refresh token not found or does not match");
    }

    /**
     * Revoke a refresh token (used during logout)
     * 
     * FIX: Added @Transactional for save() operation
     * 
     * @param rawToken the raw JWT refresh token
     * @throws RefreshTokenException if token cannot be revoked
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String email = extractEmailFromToken(rawToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        java.util.List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);

        for (RefreshToken token : tokens) {
            // CRITICAL FIX: Use BCryptPasswordEncoder.matches()
            if (passwordEncoder.matches(rawToken, token.getTokenHash())) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                log.info("✓ Refresh token revoked for user: {}", email);
                return;
            }
        }
        
        log.warn("⚠️ Refresh token to revoke not found for user: {}", email);
        throw new RefreshTokenException("Refresh token not found for revocation");
    }

    /**
     * Extract email from refresh token (via JWT parsing)
     * 
     * @param token the raw JWT refresh token
     * @return email from token subject
     */
    private String extractEmailFromToken(String token) {
        return jwtUtilService.extractUsername(token);
    }
}