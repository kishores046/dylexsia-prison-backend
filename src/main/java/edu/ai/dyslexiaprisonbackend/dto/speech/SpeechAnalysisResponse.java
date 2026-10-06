package edu.ai.dyslexiaprisonbackend.dto.speech;

public record SpeechAnalysisResponse(
        String sessionId,
        String language,
        String transcript,
        int totalWords,
        int correctWords,
        int substitutions,
        int omissions,
        int insertions,
        double accuracy,
        double wcpm,
        double avgPauseSeconds,
        double speechRiskScore,
        Double gazeScoreUsed,       // null if no matching gaze session existed
        double combinedRiskScore,
        String classification
) {
}