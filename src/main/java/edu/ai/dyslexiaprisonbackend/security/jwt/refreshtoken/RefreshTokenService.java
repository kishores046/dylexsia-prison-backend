package edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken;

import edu.ai.dyslexiaprisonbackend.model.user.User;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtilService jwtUtilService;


    public RefreshToken createRefreshToken(String userEmail, String rawToken, Date expiresAt) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));


        refreshTokenRepository.deleteAllByUser(user);

        String hashedToken = passwordEncoder.encode(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String rawToken) {

        String email = extractEmailFromToken(rawToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);

        for (RefreshToken rt : tokens) {

            if (passwordEncoder.matches(rawToken, rt.getTokenHash())) {

                if (rt.isRevoked()) {
                    throw new RuntimeException("Refresh token revoked");
                }

                if (rt.isExpired()) {
                    throw new RuntimeException("Refresh token expired");
                }

                return rt;
            }
        }

        throw new RuntimeException("Refresh token not found");
    }


    public void revokeRefreshToken(String rawToken) {

        String email = extractEmailFromToken(rawToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<RefreshToken> tokens = refreshTokenRepository.findByUser(user);

        for (RefreshToken rt : tokens) {
            if (passwordEncoder.matches(rawToken, rt.getTokenHash())) {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
                return;
            }
        }
    }


    private String extractEmailFromToken(String token) {
        return jwtUtilService.extractUsername(token);
    }
}