package edu.ai.dyslexiaprisonbackend.security.jwt;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "jwt")
@Configuration
@Getter
@Setter
public class JwtConfig {
    private String secret;
    private long expiration;
    private String issuer;
    private String audience;
    private long refreshExpiration;
}
