package edu.ai.dyslexiaprisonbackend.model.result;

public enum SessionClassification {
    LOW,
    MODERATE,
    HIGH;

    public static SessionClassification fromString(String val) {
        if (val == null || val.isBlank()) return LOW;
        String s = val.trim().toUpperCase();
        if (s.contains("HIGH")) return HIGH;
        if (s.contains("MODERATE") || s.contains("MILD")) return MODERATE;
        return LOW;
    }
}
