package edu.ai.dyslexiaprisonbackend.dto.dashboard;


import lombok.Builder;

import java.util.List;

@Builder
public record ParentDashboardResponse(

        DashboardMetricsDto metrics,

        List<ParentChildResponseDto> children,

        List<SessionResponseDto> recentSessions

) {
}