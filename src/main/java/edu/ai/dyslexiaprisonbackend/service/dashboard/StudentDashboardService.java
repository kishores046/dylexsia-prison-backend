package edu.ai.dyslexiaprisonbackend.service.dashboard;

import edu.ai.dyslexiaprisonbackend.dto.auth.LinkCodeRequest;
import edu.ai.dyslexiaprisonbackend.dto.auth.LinkCodeResponse;
import edu.ai.dyslexiaprisonbackend.dto.dashboard.*;
import edu.ai.dyslexiaprisonbackend.model.learning.LearningModuleTemplate;
import edu.ai.dyslexiaprisonbackend.model.learning.StudentModuleProgress;
import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import edu.ai.dyslexiaprisonbackend.model.user.LinkType;
import edu.ai.dyslexiaprisonbackend.model.user.RoleType;
import edu.ai.dyslexiaprisonbackend.model.user.StudentLinkCode;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import edu.ai.dyslexiaprisonbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentDashboardService {

    private final UserRepository userRepository;
    private final SessionResultRepository sessionResultRepository;
    private final LearningModuleTemplateRepository learningModuleTemplateRepository;
    private final StudentModuleProgressRepository studentModuleProgressRepository;
    private final StudentLinkCodeRepository studentLinkCodeRepository;
    public List<SessionResponseDto> getStudentSessions(
            Authentication authentication) {

        User student = getAuthenticatedUser(authentication);

        List<SessionResult> results =
                sessionResultRepository
                        .findByStudentIdOrderByTimestampDesc(
                                student.getId()
                        );

        return results.stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public LearningPlanResponseDto getLearningPlan(
            Authentication authentication) {

        User student = getAuthenticatedUser(authentication);

        Optional<SessionResult> latestResultOpt =
                sessionResultRepository
                        .findFirstByStudentIdOrderByTimestampDesc(
                                student.getId()
                        );

        if (latestResultOpt.isEmpty()
                || latestResultOpt.get().getClassification() == null
                || "LOW".equalsIgnoreCase(
                latestResultOpt.get()
                        .getClassification()
                        .name()
        )) {

            return LearningPlanResponseDto.builder()
                    .classification("LOW")
                    .language("en")
                    .modules(List.of())
                    .build();
        }

        String tier =
                latestResultOpt.get()
                        .getClassification()
                        .name();

        List<LearningModuleTemplate> templates =
                learningModuleTemplateRepository
                        .findByRiskTierOrderByOrderIndexAsc(tier);

        List<ModuleDto> modules = new ArrayList<>();

        for (LearningModuleTemplate template : templates) {

            StudentModuleProgress progress =
                    studentModuleProgressRepository
                            .findByStudentIdAndModuleTemplateId(
                                    student.getId(),
                                    template.getId()
                            )
                            .orElseGet(() ->
                                    createProgress(
                                            student.getId(),
                                            template.getId()
                                    )
                            );

            modules.add(
                    ModuleDto.builder()
                            .id(template.getId())
                            .title(template.getTitle())
                            .type(template.getType())
                            .completed(progress.isCompleted())
                            .audioUrl(template.getAudioUrl())
                            .build()
            );
        }

        return LearningPlanResponseDto.builder()
                .classification(tier)
                .language("en")
                .modules(modules)
                .build();
    }

    public ModuleDto completeModule(
            Long moduleId,
            Authentication authentication) {

        User student = getAuthenticatedUser(authentication);

        LearningModuleTemplate template =
                learningModuleTemplateRepository
                        .findById(moduleId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Module template not found: "
                                                + moduleId
                                )
                        );

        StudentModuleProgress progress =
                studentModuleProgressRepository
                        .findByStudentIdAndModuleTemplateId(
                                student.getId(),
                                moduleId
                        )
                        .orElseGet(() ->
                                createProgress(
                                        student.getId(),
                                        moduleId
                                )
                        );

        progress.setCompleted(true);
        progress.setCompletedAt(LocalDateTime.now());

        studentModuleProgressRepository.save(progress);

        return ModuleDto.builder()
                .id(template.getId())
                .title(template.getTitle())
                .type(template.getType())
                .completed(true)
                .audioUrl(template.getAudioUrl())
                .build();
    }

    private StudentModuleProgress createProgress(
            Long studentId,
            Long moduleId) {

        return studentModuleProgressRepository.save(
                StudentModuleProgress.builder()
                        .studentId(studentId)
                        .moduleTemplateId(moduleId)
                        .completed(false)
                        .build()
        );
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
    public LinkCodeResponse generateLinkCode(
            LinkCodeRequest request,
            Authentication authentication) {

        User student = getAuthenticatedUser(authentication);

        if (student.getRoleType() != RoleType.STUDENT) {
            throw new IllegalStateException(
                    "Only students can generate linking codes"
            );
        }

        LinkType type = request.type();

        /*
         * Reuse an existing valid code if one already exists.
         */
        Optional<StudentLinkCode> existing =
                studentLinkCodeRepository
                        .findByStudentIdAndLinkTypeAndActiveTrue(
                                student.getId(),
                                type
                        );

        if (existing.isPresent()) {

            StudentLinkCode code = existing.get();

            if (code.getExpiresAt().isAfter(LocalDateTime.now())) {

                return new LinkCodeResponse(
                        code.getCode(),
                        code.getLinkType(),
                        code.getExpiresAt()
                );
            }

            code.setActive(false);

            studentLinkCodeRepository.save(code);
        }

        String generatedCode = generateUniqueCode(type);

        LocalDateTime expiresAt =
                LocalDateTime.now().plusDays(7);

        StudentLinkCode linkCode =
                StudentLinkCode.builder()
                        .studentId(student.getId())
                        .code(generatedCode)
                        .linkType(type)
                        .active(true)
                        .expiresAt(expiresAt)
                        .build();

        studentLinkCodeRepository.save(linkCode);

        return new LinkCodeResponse(
                generatedCode,
                type,
                expiresAt
        );
    }
    private String generateUniqueCode(LinkType type) {

        String prefix =
                type == LinkType.PARENT
                        ? "PAR"
                        : "TEA";

        String code;

        do {

            code = prefix + "-" + randomCode();

        } while (
                studentLinkCodeRepository
                        .findByCodeAndActiveTrue(code)
                        .isPresent()
        );

        return code;
    }

    private String randomCode() {

        String characters =
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        SecureRandom random = new SecureRandom();

        StringBuilder result =
                new StringBuilder(6);

        for (int i = 0; i < 6; i++) {

            result.append(
                    characters.charAt(
                            random.nextInt(characters.length())
                    )
            );
        }

        return result.toString();
    }


    public StudentDashboardResponse getDashboard(
            Authentication authentication) {

        User student =
                getAuthenticatedUser(authentication);

        List<SessionResponseDto> sessions =
                getStudentSessions(authentication);

        List<SessionResponseDto> recentSessions =
                sessions.stream()
                        .limit(5)
                        .toList();

        LearningPlanResponseDto learningPlan =
                getLearningPlan(authentication);

        DashboardMetricsDto metrics =
                buildStudentMetrics(sessions);

        return StudentDashboardResponse.builder()
                .metrics(metrics)
                .learningPlan(learningPlan)
                .recentSessions(recentSessions)
                .build();
    }

    private DashboardMetricsDto buildStudentMetrics(
            List<SessionResponseDto> sessions) {

        Double averageRisk =
                sessions.stream()
                        .map(SessionResponseDto::getRiskScore)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

        SessionResponseDto latest =
                sessions.isEmpty()
                        ? null
                        : sessions.get(0);

        return DashboardMetricsDto.builder()
                .totalSessions(sessions.size())
                .totalStudents(0)
                .activeStudents(0)
                .latestRiskScore(
                        latest != null
                                ? latest.getRiskScore()
                                : null
                )
                .latestClassification(
                        latest != null
                                ? latest.getClassification()
                                : null
                )
                .latestSessionDate(
                        latest != null
                                ? latest.getDate()
                                : null
                )
                .averageRiskScore(averageRisk)
                .build();
    }
}