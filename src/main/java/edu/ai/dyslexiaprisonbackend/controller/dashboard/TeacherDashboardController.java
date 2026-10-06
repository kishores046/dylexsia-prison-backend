package edu.ai.dyslexiaprisonbackend.controller.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkChildRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkStudentRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkedStudentResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.SessionResponseDto;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.TeacherDashboardResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.TeacherStudentResponseDto;
import edu.ai.dyslexiaprisonbackend.service.dashboard.TeacherDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers/me")
@RequiredArgsConstructor
public class TeacherDashboardController {

    private final TeacherDashboardService teacherDashboardService;

    @GetMapping("/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<TeacherStudentResponseDto>> getStudents(
            Authentication authentication) {

        return ResponseEntity.ok(
                teacherDashboardService.getStudents(
                        authentication
                )
        );
    }

    @GetMapping("/students/{studentId}/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<SessionResponseDto>> getStudentSessions(
            @PathVariable Long studentId,
            Authentication authentication) {

        return ResponseEntity.ok(
                teacherDashboardService.getStudentSessions(
                        studentId,
                        authentication
                )
        );
    }

    @PostMapping("/link-student")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<LinkedStudentResponse> linkStudent(
            @Valid @RequestBody LinkStudentRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                teacherDashboardService.linkStudent(
                        request,
                        authentication
                )
        );
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<TeacherDashboardResponse> getDashboard(
            Authentication authentication) {

        return ResponseEntity.ok(
                teacherDashboardService.getDashboard(
                        authentication
                )
        );
    }
}