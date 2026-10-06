package edu.ai.dyslexiaprisonbackend.dto.dashboard;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record DashboardMetricsDto(

        long totalSessions,

        long totalStudents,

        long activeStudents,

        Double latestRiskScore,

        String latestClassification,

        LocalDateTime latestSessionDate,

        Double averageRiskScore

) {
}