package edu.ai.dyslexiaprisonbackend.security.jwt.blacklist;

public interface TokenBlacklistService {

    void blacklistToken(String token, String userEmail);
    boolean isBlacklisted(String token);
}
