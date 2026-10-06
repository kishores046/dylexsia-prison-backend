package edu.ai.dyslexiaprisonbackend.dto.dashboard;


import lombok.Builder;

import java.util.List;

@Builder
public record TeacherDashboardResponse(

        DashboardMetricsDto metrics,

        List<TeacherStudentResponseDto> students,

        List<SessionResponseDto> recentSessions

) {
}