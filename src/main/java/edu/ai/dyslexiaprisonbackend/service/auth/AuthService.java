package edu.ai.dyslexiaprisonbackend.service.auth;

import edu.ai.dyslexiaprisonbackend.dto.auth.*;
import edu.ai.dyslexiaprisonbackend.exception.AuthenticationFailedException;
import edu.ai.dyslexiaprisonbackend.exception.RefreshTokenException;
import edu.ai.dyslexiaprisonbackend.exception.UserNotFoundException;
import edu.ai.dyslexiaprisonbackend.model.user.RoleType;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import edu.ai.dyslexiaprisonbackend.model.user.UserStatus;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import edu.ai.dyslexiaprisonbackend.security.jwt.blacklist.TokenBlacklistService;
import edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken.RefreshToken;
import edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken.RefreshTokenRepository;
import edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken.RefreshTokenService;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * AuthService - Handles user registration, login, refresh, and logout
 * 
 * PRODUCTION SECURITY:
 * - Uses custom exceptions instead of generic RuntimeException
 * - Handles token refresh with proper rotation
 * - Properly validates and revokes tokens on logout
 * - BCrypt password encoding for security
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilService jwtUtilService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Login user and return JWT tokens
     * 
     * @param request login credentials
     * @return LoginResponse with access and refresh tokens
     * @throws AuthenticationFailedException if credentials are invalid
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        if (user.isBlocked()) {
            throw new AuthenticationFailedException("User account is blocked");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtUtilService.generateToken(userDetails);
        String rawRefresh = jwtUtilService.generateRefreshToken(userDetails);

        Date accessExp = jwtUtilService.extractExpiration(accessToken);
        Date refreshExp = jwtUtilService.extractExpiration(rawRefresh);

        refreshTokenService.createRefreshToken(user.getEmail(), rawRefresh, refreshExp);

        log.info("✓ User logged in: {}", user.getEmail());

        return new LoginResponse(
                accessToken,
                rawRefresh,
                "Bearer",
                user.getRoleType().name(),
                accessExp.getTime()
        );
    }

    /**
     * Register a new user
     * 
     * @param request registration data
     * @return RegisterResponse with user info
     * @throws AuthenticationFailedException if email already exists
     */
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new AuthenticationFailedException("Email already registered");
        }


        validateRegistrationRole(request.role());
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoleType(request.role());
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(request.gender().charAt(0));
        user.setUserStatus(UserStatus.ACTIVE);
        user.setBlocked(false);

        User saved = userRepository.save(user);

        log.info("✓ User registered: {}", saved.getEmail());

        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRoleType().name(),
                "User registered successfully"
        );
    }

    /**
     * Logout user - blacklist the access token and revoke refresh token
     * 
     * FIX: Fixed logic to properly check token type
     * Token type "refresh" means it's a refresh token - cannot logout with refresh token
     * 
     * @param token JWT access token (from Authorization header, without "Bearer " prefix)
     * @throws RefreshTokenException if attempting logout with refresh token
     */
    @Transactional
    public void logout(String token) {
        String type;
        String userEmail;

        try {
            var claims = jwtUtilService.parseClaimsForLogout(token);
            type = claims.get("type", String.class);
            userEmail = claims.getSubject();
        } catch (Exception e) {
            log.warn("⚠️ Invalid token provided for logout: {}", e.getMessage());
            throw new AuthenticationFailedException("Invalid token");
        }

        // FIX: Changed logic - if type IS "refresh", reject it
        // Access tokens have type='access' (or null), refresh tokens have type='refresh'
        if ("refresh".equals(type)) {
            throw new RefreshTokenException("Cannot logout with refresh token. Use access token.");
        }

        tokenBlacklistService.blacklistToken(token, userEmail);

        userRepository.findByEmail(userEmail)
                .ifPresent(refreshTokenRepository::deleteAllByUser);

        log.info("✓ User logged out: {}", userEmail);
    }

    /**
     * Refresh access token using refresh token
     * 
     * Implementation:
     * - Validate refresh token
     * - Generate new access token
     * - Rotate refresh token (revoke old, issue new)
     * 
     * @param request containing refresh token
     * @return RefreshResponse with new tokens
     * @throws RefreshTokenException if refresh token is invalid/expired/revoked
     */
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenService.validateRefreshToken(request.refreshToken());

        User user = stored.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // Rotate: revoke old, issue new
        refreshTokenService.revokeRefreshToken(request.refreshToken());

        String newAccess = jwtUtilService.generateToken(userDetails);
        String newRefresh = jwtUtilService.generateRefreshToken(userDetails);

        Date newAccessExp = jwtUtilService.extractExpiration(newAccess);
        Date newRefreshExp = jwtUtilService.extractExpiration(newRefresh);

        refreshTokenService.createRefreshToken(user.getEmail(), newRefresh, newRefreshExp);

        log.info("✓ Token refreshed for user: {}", user.getEmail());

        return new RefreshResponse(
                newAccess,
                newRefresh,
                "Bearer",
                newAccessExp.getTime()
        );
    }


    private void validateRegistrationRole(RoleType role) {

        if (role == null) {
            throw new AuthenticationFailedException(
                    "Role is required"
            );
        }

        if (role == RoleType.ADMIN) {
            throw new AuthenticationFailedException(
                    "Admin registration is not allowed"
            );
        }
    }
}