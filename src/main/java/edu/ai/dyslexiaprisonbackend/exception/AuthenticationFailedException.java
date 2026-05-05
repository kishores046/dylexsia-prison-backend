package edu.ai.dyslexiaprisonbackend.exception;

/**
 * Thrown when authentication fails (invalid credentials, expired token, etc.)
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

