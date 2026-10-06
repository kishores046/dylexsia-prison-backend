package edu.ai.dyslexiaprisonbackend.dto.dashboard;



import lombok.Builder;

import java.util.List;

@Builder
public record StudentDashboardResponse(

        DashboardMetricsDto metrics,

        LearningPlanResponseDto learningPlan,

        List<SessionResponseDto> recentSessions

) {
}