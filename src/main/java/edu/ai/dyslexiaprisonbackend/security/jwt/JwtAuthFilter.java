package edu.ai.dyslexiaprisonbackend.security.jwt;


import edu.ai.dyslexiaprisonbackend.security.jwt.blacklist.TokenBlacklistService;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter
 * 
 * Processes incoming HTTP requests with JWT Bearer tokens
 * Validates tokens and sets up Spring Security context
 * 
 * SECURITY FIXES:
 * - Does NOT log usernames (avoids sensitive data in logs)
 * - Uses try-catch with detailed error logging
 * - Checks token blacklist before validation
 */
@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private JwtUtilService jwtUtil;
    private CustomUserDetailsService userDetailsService;
    private TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);

                // Check blacklist first (efficient)
                if (tokenBlacklistService.isBlacklisted(token)) {
                    log.debug("⚠️ Token is blacklisted");
                    filterChain.doFilter(request, response);
                    return;
                }

                try {
                    username = jwtUtil.extractUsername(token);
                } catch (Exception e) {
                    log.debug("⚠️ Error extracting username from token: {}", e.getMessage());
                }
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("✓ JWT validated successfully");
                } else {
                    log.debug("⚠️ Invalid JWT token");
                }
            }
        } catch (Exception e) {
            log.debug("⚠️ JWT Authentication Error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.startsWith("/api/auth/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/h2-console/")
                || path.startsWith("/error");
    }
}