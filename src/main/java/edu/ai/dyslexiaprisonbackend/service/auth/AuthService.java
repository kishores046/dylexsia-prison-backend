package edu.ai.dyslexiaprisonbackend.service.auth;

import edu.ai.dyslexiaprisonbackend.dto.auth.*;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilService jwtUtilService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.isBlocked()) {
            throw new RuntimeException("User is blocked");
        }

        UserDetails userDetails = userDetailsService
                .loadUserByUsername(user.getEmail());

        String accessToken = jwtUtilService.generateToken(userDetails);
        String rawRefresh  = jwtUtilService.generateRefreshToken(userDetails);

        Date accessExp  = jwtUtilService.extractExpiration(accessToken);
        Date refreshExp = jwtUtilService.extractExpiration(rawRefresh);

        refreshTokenService.createRefreshToken(user.getEmail(), rawRefresh, refreshExp);

        return new LoginResponse(
                accessToken,
                rawRefresh,
                "Bearer",
                user.getRoleType().name(),
                accessExp.getTime()
        );
    }


    public RegisterResponse register(RegisterRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoleType(RoleType.STUDENT);
        user.setDateOfBirth(request.dateOfBirth());
        user.setGender(request.gender().charAt(0));
        user.setUserStatus(UserStatus.ACTIVE);
        user.setBlocked(false);

        User saved = userRepository.save(user);

        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRoleType().name(),
                "User registered successfully"
        );
    }


    @Transactional
    public void logout(String token) {

        String type;
        String userEmail;

        try {
            var claims = jwtUtilService.parseClaimsForLogout(token);
            type = claims.get("type", String.class);
            userEmail = claims.getSubject();
        } catch (Exception e) {
            throw new RuntimeException("Invalid token: " + e.getMessage());
        }

        if ("refresh".equals(type)) {
            throw new RuntimeException("Refresh token cannot be used for logout");
        }

        tokenBlacklistService.blacklistToken(token, userEmail);

        userRepository.findByEmail(userEmail)
                .ifPresent(refreshTokenRepository::deleteAllByUser);
    }



    public RefreshResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenService.validateRefreshToken(request.refreshToken());

        User user = stored.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // Rotate: revoke old, issue new
        refreshTokenService.revokeRefreshToken(request.refreshToken());

        String newAccess  = jwtUtilService.generateToken(userDetails);
        String newRefresh = jwtUtilService.generateRefreshToken(userDetails);

        Date newAccessExp  = jwtUtilService.extractExpiration(newAccess);
        Date newRefreshExp = jwtUtilService.extractExpiration(newRefresh);

        refreshTokenService.createRefreshToken(user.getEmail(), newRefresh, newRefreshExp);

        return new RefreshResponse(
                newAccess,
                newRefresh,
                "Bearer",
                newAccessExp.getTime()
        );
    }
}