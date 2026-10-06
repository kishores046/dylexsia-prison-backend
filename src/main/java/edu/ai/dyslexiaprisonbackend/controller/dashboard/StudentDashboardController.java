package edu.ai.dyslexiaprisonbackend.controller.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkCodeRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkCodeResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.LearningPlanResponseDto;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.ModuleDto;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.SessionResponseDto;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.StudentDashboardResponse;
import edu.ai.dyslexiaprisonbackend.service.dashboard.StudentDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students/me")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<SessionResponseDto>> getStudentSessions(
            Authentication authentication) {

        return ResponseEntity.ok(
                studentDashboardService.getStudentSessions(
                        authentication
                )
        );
    }

    @GetMapping("/learning-plan")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<LearningPlanResponseDto> getLearningPlan(
            Authentication authentication) {

        return ResponseEntity.ok(
                studentDashboardService.getLearningPlan(
                        authentication
                )
        );
    }

    @PostMapping("/learning-plan/{moduleId}/complete")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ModuleDto> completeModule(
            @PathVariable Long moduleId,
            Authentication authentication) {

        return ResponseEntity.ok(
                studentDashboardService.completeModule(
                        moduleId,
                        authentication
                )
        );
    }

    @PostMapping("/link-codes")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<LinkCodeResponse> generateLinkCode(
            @Valid @RequestBody LinkCodeRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                studentDashboardService.generateLinkCode(
                        request,
                        authentication
                )
        );
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentDashboardResponse> getDashboard(
            Authentication authentication) {

        return ResponseEntity.ok(
                studentDashboardService.getDashboard(
                        authentication
                )
        );
    }
}