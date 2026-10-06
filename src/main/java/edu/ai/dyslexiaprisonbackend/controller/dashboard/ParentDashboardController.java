package edu.ai.dyslexiaprisonbackend.controller.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkChildRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkStudentRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkedStudentResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.ParentChildResponseDto;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.ParentDashboardResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.SessionResponseDto;
import edu.ai.dyslexiaprisonbackend.service.dashboard.ParentDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parents/me")
@RequiredArgsConstructor
public class ParentDashboardController {

    private final ParentDashboardService parentDashboardService;

    @GetMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<ParentChildResponseDto>> getChildren(
            Authentication authentication) {

        return ResponseEntity.ok(
                parentDashboardService.getChildren(authentication)
        );
    }

    @GetMapping("/children/{studentId}/sessions")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<SessionResponseDto>> getChildSessions(
            @PathVariable Long studentId,
            Authentication authentication) {

        return ResponseEntity.ok(
                parentDashboardService.getChildSessions(
                        studentId,
                        authentication
                )
        );
    }

    @GetMapping("/children/{studentId}/report.pdf")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<byte[]> getChildPdfReport(
            @PathVariable Long studentId,
            Authentication authentication) {

        return parentDashboardService.generateChildPdfReport(
                studentId,
                authentication
        );
    }

    @PostMapping("/link-child")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<LinkedStudentResponse> linkChild(
            @Valid @RequestBody LinkStudentRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                parentDashboardService.linkChild(
                        request,
                        authentication
                )
        );
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ParentDashboardResponse> getDashboard(
            Authentication authentication) {

        return ResponseEntity.ok(
                parentDashboardService.getDashboard(
                        authentication
                )
        );
    }
}