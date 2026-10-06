package edu.ai.dyslexiaprisonbackend.service.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkChildRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkStudentRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkedStudentResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.*;
import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import edu.ai.dyslexiaprisonbackend.model.user.*;
import edu.ai.dyslexiaprisonbackend.repository.SessionResultRepository;
import edu.ai.dyslexiaprisonbackend.repository.StudentLinkCodeRepository;
import edu.ai.dyslexiaprisonbackend.repository.TeacherClassLinkRepository;
import edu.ai.dyslexiaprisonbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class TeacherDashboardService {

    private final UserRepository userRepository;
    private final TeacherClassLinkRepository teacherClassLinkRepository;
    private final SessionResultRepository sessionResultRepository;
    private final StudentLinkCodeRepository studentLinkCodeRepository;

    public List<TeacherStudentResponseDto> getStudents(
            Authentication authentication) {

        User teacher = getAuthenticatedUser(authentication);

        List<TeacherClassLink> links =
                teacherClassLinkRepository
                        .findByTeacherId(teacher.getId());

        List<TeacherStudentResponseDto> response =
                new ArrayList<>();

        for (TeacherClassLink link : links) {

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

            LocalDateTime lastSessionDate = latestOpt
                    .map(SessionResult::getTimestamp)
                    .orElse(null);

            response.add(
                    TeacherStudentResponseDto.builder()
                            .studentId(student.getId())
                            .name(name)
                            .latestClassification(classification)
                            .latestScore(score)
                            .lastSessionDate(lastSessionDate)
                            .build()
            );
        }

        return response;
    }

    public List<SessionResponseDto> getStudentSessions(
            Long studentId,
            Authentication authentication) {

        User teacher = getAuthenticatedUser(authentication);

        boolean linked =
                teacherClassLinkRepository
                        .existsByTeacherIdAndStudentId(
                                teacher.getId(),
                                studentId
                        );

        if (!linked) {
            throw new StudentAccessDeniedException(
                    "Teacher is not linked to this student"
            );
        }

        return sessionResultRepository
                .findByStudentIdOrderByTimestampDesc(studentId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    private SessionResponseDto toSessionResponse(
            SessionResult session) {

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
    public LinkedStudentResponse linkStudent(
            LinkStudentRequest request,
            Authentication authentication) {

        User teacher = getAuthenticatedUser(authentication);

        if (teacher.getRoleType() != RoleType.TEACHER) {
            throw new IllegalStateException(
                    "Only teachers can link students"
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

        if (linkCode.getLinkType() != LinkType.TEACHER) {
            throw new IllegalArgumentException(
                    "This code is not a teacher linking code"
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
                teacherClassLinkRepository
                        .existsByTeacherIdAndStudentId(
                                teacher.getId(),
                                student.getId()
                        );

        if (alreadyLinked) {
            throw new IllegalStateException(
                    "This student is already linked to your account"
            );
        }

        TeacherClassLink link =
                new TeacherClassLink();

        link.setTeacherId(teacher.getId());
        link.setStudentId(student.getId());

        teacherClassLinkRepository.save(link);

        return new LinkedStudentResponse(
                student.getId(),
                getDisplayName(student),
                "Student linked successfully"
        );
    }
    private String getDisplayName(User user) {

        return user.getUsername() != null &&
                !user.getUsername().isBlank()
                ? user.getUsername()
                : user.getEmail();
    }


    public TeacherDashboardResponse getDashboard(
            Authentication authentication) {

        User teacher =
                getAuthenticatedUser(authentication);

        List<TeacherStudentResponseDto> students =
                getStudents(authentication);

        List<Long> studentIds =
                teacherClassLinkRepository
                        .findByTeacherId(teacher.getId())
                        .stream()
                        .map(TeacherClassLink::getStudentId)
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
                        .limit(10)
                        .map(this::toSessionResponse)
                        .toList();

        DashboardMetricsDto metrics =
                buildTeacherMetrics(
                        students,
                        allSessions
                );

        return TeacherDashboardResponse.builder()
                .metrics(metrics)
                .students(students)
                .recentSessions(recentSessions)
                .build();
    }

    private DashboardMetricsDto buildTeacherMetrics(
            List<TeacherStudentResponseDto> students,
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
                .totalStudents(students.size())
                .activeStudents(
                        students.stream()
                                .filter(s ->
                                        s.getLastSessionDate() != null)
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