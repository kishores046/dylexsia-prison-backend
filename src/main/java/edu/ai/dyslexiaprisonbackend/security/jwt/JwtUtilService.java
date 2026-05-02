package edu.ai.dyslexiaprisonbackend.security.jwt;


import edu.ai.dyslexiaprisonbackend.security.domain.MyUserDetails;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtUtilService {

    private final JwtConfig jwtConfig;
    private final CustomUserDetailsService userDetailsService;

    private SecretKey getSignedKey(){
        byte[] bytes=jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(UserDetails userDetails){
        MyUserDetails myUserDetails=(MyUserDetails)userDetails;
        List<String> roles=myUserDetails.getAuthorities().stream().map(Object::toString).toList();

        long now=System.currentTimeMillis();
        long exp=jwtConfig.getExpiration()+now;
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .expiration(new Date(exp))
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .claim("roles",roles)
                .id(UUID.randomUUID().toString())
                .signWith(getSignedKey())
                .compact();
    }


    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();


            String issuer = claims.getIssuer();
            if (!jwtConfig.getIssuer().equals(issuer)) {
                log.warn("Invalid issuer: expected {}, got {}", jwtConfig.getIssuer(), issuer);
                return false;
            }

            Object audClaim = claims.get("aud");

            if (audClaim instanceof String aud) {
                if (!jwtConfig.getAudience().equals(aud)) return false;
            } else if (audClaim instanceof Collection<?> audiences) {
                if (!audiences.contains(jwtConfig.getAudience())) return false;
            } else {
                return false;
            }

            return username != null
                    && username.equals(userDetails.getUsername())
                    && isTokenNotExpired(claims);
        } catch (Exception e) {
            log.info("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignedKey())
                    .requireAudience(jwtConfig.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }catch (JwtException | IllegalArgumentException e) {
            throw new SecurityException("Invalid or tampered JWT", e);
        }
    }

    public boolean isTokenNotExpired(Claims claims){
        return new Date(System.currentTimeMillis()).before(claims.getExpiration());
    }


    public String extractUsername(String token) {
        Claims claims=parseClaims(token);
        return claims.getSubject();
    }


    public String extractTokenId(String token) {
        Claims claims = parseClaims(token);
        return claims.getId();
    }

    public Date extractExpiration(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        long exp = jwtConfig.getRefreshExpiration() + now;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .expiration(new Date(exp))
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .signWith(getSignedKey())
                .compact();
    }

    /**
     * Parses claims without expiration check — used for logout/blacklisting.
     * Signature is still verified.
     */
    public Claims parseClaimsForLogout(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignedKey())
                    .requireAudience(jwtConfig.getAudience())
                    .clockSkewSeconds(Long.MAX_VALUE / 1000) // ignore expiry
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new SecurityException("Invalid or tampered JWT", e);
        }
    }
}
