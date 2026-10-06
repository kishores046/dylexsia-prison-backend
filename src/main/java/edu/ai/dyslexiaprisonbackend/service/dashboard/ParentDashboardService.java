package edu.ai.dyslexiaprisonbackend.service.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkChildRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkStudentRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkedStudentResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.*;
import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import edu.ai.dyslexiaprisonbackend.model.user.*;
import edu.ai.dyslexiaprisonbackend.repository.ParentChildLinkRepository;
import edu.ai.dyslexiaprisonbackend.repository.SessionResultRepository;
import edu.ai.dyslexiaprisonbackend.repository.StudentLinkCodeRepository;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import edu.ai.dyslexiaprisonbackend.service.report.PdfReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ParentDashboardService {

    private final UserRepository userRepository;
    private final ParentChildLinkRepository parentChildLinkRepository;
    private final SessionResultRepository sessionResultRepository;
    private final PdfReportService pdfReportService;
    private final StudentLinkCodeRepository studentLinkCodeRepository;

    public List<ParentChildResponseDto> getChildren(
            Authentication authentication) {

        User parent = getAuthenticatedUser(authentication);

        List<ParentChildLink> links =
                parentChildLinkRepository.findByParentId(parent.getId());

        List<ParentChildResponseDto> response = new ArrayList<>();

        for (ParentChildLink link : links) {

            Optional<User> studentOpt =
                    userRepository.findById(link.getStudentId());

            if (studentOpt.isEmpty()) {
                continue;
            }

            User student = studentOpt.get();

            String name = student.getUsername() != null
                    ? student.getUsername()
                    : student.getEmail();

            Optional<SessionResult> latestOpt =
                    sessionResultRepository
                            .findFirstByStudentIdOrderByTimestampDesc(
                                    student.getId()
                            );

            String classification = latestOpt
                    .map(SessionResult::getClassification)
                    .map(Enum::name)
                    .orElse(null);

            Double score = latestOpt
                    .map(SessionResult::getRiskScore)
                    .orElse(null);

            response.add(
                    ParentChildResponseDto.builder()
                            .studentId(student.getId())
                            .name(name)
                            .latestClassification(classification)
                            .latestScore(score)
                            .build()
            );
        }

        return response;
    }

    public List<SessionResponseDto> getChildSessions(
            Long studentId,
            Authentication authentication) {

        User parent = getAuthenticatedUser(authentication);

        boolean linked =
                parentChildLinkRepository
                        .existsByParentIdAndStudentId(
                                parent.getId(),
                                studentId
                        );

        if (!linked) {
            throw new IllegalStateException(
                    "Parent is not linked to this student"
            );
        }

        List<SessionResult> results =
                sessionResultRepository
                        .findByStudentIdOrderByTimestampDesc(studentId);

        return results.stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public ResponseEntity<byte[]> generateChildPdfReport(
            Long studentId,
            Authentication authentication) {

        User parent = getAuthenticatedUser(authentication);

        boolean linked =
                parentChildLinkRepository
                        .existsByParentIdAndStudentId(
                                parent.getId(),
                                studentId
                        );

        if (!linked) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build();
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student not found: " + studentId
                        )
                );

        List<SessionResult> sessions =
                sessionResultRepository
                        .findByStudentIdOrderByTimestampDesc(studentId);

        byte[] pdf =
                pdfReportService.generateStudentReportPdf(
                        student,
                        sessions
                );

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_PDF);

        headers.setContentDispositionFormData(
                "attachment",
                "student-" + studentId + "-report.pdf"
        );

        return new ResponseEntity<>(
                pdf,
                headers,
                HttpStatus.OK
        );
    }

    private SessionResponseDto toSessionResponse(SessionResult session) {

        return SessionResponseDto.builder()
                .sessionId(session.getSessionId())
                .date(session.getTimestamp())
                .riskScore(session.getRiskScore())
                .classification(
                        session.getClassification() != null
                                ? session.getClassification().name()
                                : "LOW"
                )
                .breakdown(
                        BreakdownDto.builder()
                                .ruleScore(session.getRuleScore())
                                .rfScore(session.getRfScore())
                                .build()
                )
                .build();
    }

    private User getAuthenticatedUser(
            Authentication authentication) {

        String username = authentication.getName();

        return userRepository
                .findByEmailOrUsername(username, username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found: " + username
                        )
                );
    }


    @Transactional
    public LinkedStudentResponse linkChild(
            LinkStudentRequest request,
            Authentication authentication) {

        User parent = getAuthenticatedUser(authentication);

        if (parent.getRoleType() != RoleType.PARENT) {
            throw new IllegalStateException(
                    "Only parents can link students"
            );
        }

        String normalizedCode =
                request.code()
                        .trim()
                        .toUpperCase();

        StudentLinkCode linkCode =
                studentLinkCodeRepository
                        .findByCodeAndActiveTrue(normalizedCode)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid or expired linking code"
                                )
                        );

        if (linkCode.getLinkType() != LinkType.PARENT) {
            throw new IllegalArgumentException(
                    "This code is not a parent linking code"
            );
        }

        if (linkCode.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            linkCode.setActive(false);
            studentLinkCodeRepository.save(linkCode);

            throw new IllegalArgumentException(
                    "This linking code has expired"
            );
        }

        User student =
                userRepository.findById(
                        linkCode.getStudentId()
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student account not found"
                        )
                );

        if (student.getRoleType() != RoleType.STUDENT) {
            throw new IllegalStateException(
                    "The linked account is not a student"
            );
        }

        boolean alreadyLinked =
                parentChildLinkRepository
                        .existsByParentIdAndStudentId(
                                parent.getId(),
                                student.getId()
                        );

        if (alreadyLinked) {
            throw new IllegalStateException(
                    "This student is already linked to your account"
            );
        }

        ParentChildLink link =
                new ParentChildLink();

        link.setParentId(parent.getId());
        link.setStudentId(student.getId());

        parentChildLinkRepository.save(link);

        return new LinkedStudentResponse(
                student.getId(),
                getDisplayName(student),
                "Student linked successfully"
        );
    }

    private String getDisplayName(User user) {

        if (user.getUsername() != null &&
                !user.getUsername().isBlank()) {

            return user.getUsername();
        }

        return user.getEmail();
    }


    public ParentDashboardResponse getDashboard(
            Authentication authentication) {

        User parent = getAuthenticatedUser(authentication);

        List<ParentChildResponseDto> children =
                getChildren(authentication);

        List<Long> studentIds =
                parentChildLinkRepository
                        .findByParentId(parent.getId())
                        .stream()
                        .map(ParentChildLink::getStudentId)
                        .toList();

        List<SessionResult> allSessions =
                studentIds.stream()
                        .flatMap(id ->
                                sessionResultRepository
                                        .findByStudentIdOrderByTimestampDesc(id)
                                        .stream()
                        )
                        .sorted(
                                (a, b) ->
                                        b.getTimestamp()
                                                .compareTo(a.getTimestamp())
                        )
                        .toList();

        List<SessionResponseDto> recentSessions =
                allSessions.stream()
                        .limit(5)
                        .map(this::toSessionResponse)
                        .toList();

        DashboardMetricsDto metrics =
                buildParentMetrics(
                        children,
                        allSessions
                );

        return ParentDashboardResponse.builder()
                .metrics(metrics)
                .children(children)
                .recentSessions(recentSessions)
                .build();
    }


    private DashboardMetricsDto buildParentMetrics(
            List<ParentChildResponseDto> children,
            List<SessionResult> sessions) {

        Double averageRisk =
                sessions.isEmpty()
                        ? null
                        : sessions.stream()
                        .map(SessionResult::getRiskScore)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        SessionResult latest =
                sessions.isEmpty()
                        ? null
                        : sessions.get(0);

        return DashboardMetricsDto.builder()
                .totalStudents(children.size())
                .activeStudents(
                        children.stream()
                                .filter(c ->
                                        c.getLatestScore() != null)
                                .count()
                )
                .totalSessions(sessions.size())
                .latestRiskScore(
                        latest != null
                                ? latest.getRiskScore()
                                : null
                )
                .latestClassification(
                        latest != null &&
                                latest.getClassification() != null
                                ? latest.getClassification().name()
                                : null
                )
                .latestSessionDate(
                        latest != null
                                ? latest.getTimestamp()
                                : null
                )
                .averageRiskScore(averageRisk)
                .build();
    }
}