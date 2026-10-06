package edu.ai.dyslexiaprisonbackend.service.dashboard;

public class StudentAccessDeniedException extends RuntimeException {
    public StudentAccessDeniedException(String s) {
        super(s);
    }
}
