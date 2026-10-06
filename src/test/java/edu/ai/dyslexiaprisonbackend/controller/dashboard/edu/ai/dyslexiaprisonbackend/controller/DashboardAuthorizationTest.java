package edu.ai.dyslexiaprisonbackend.controller.dashboard.edu.ai.dyslexiaprisonbackend.controller;

import edu.ai.dyslexiaprisonbackend.model.result.SessionClassification;
import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import edu.ai.dyslexiaprisonbackend.model.user.*;
import edu.ai.dyslexiaprisonbackend.repository.*;
import edu.ai.dyslexiaprisonbackend.security.jwt.JwtUtilService;
import edu.ai.dyslexiaprisonbackend.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("dev")
public class DashboardAuthorizationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentChildLinkRepository parentChildLinkRepository;

    @Autowired
    private TeacherClassLinkRepository teacherClassLinkRepository;

    @Autowired
    private SessionResultRepository sessionResultRepository;

    @Autowired
    private JwtUtilService jwtUtilService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User student1;
    private User parent1;
    private User parent2;
    private User teacher1;
    private User teacher2;

    private String parent1Token;
    private String parent2Token;
    private String teacher1Token;
    private String teacher2Token;
    private String student1Token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        sessionResultRepository.deleteAll();
        parentChildLinkRepository.deleteAll();
        teacherClassLinkRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Users
        student1 = createUser("s1_user", "s1@example.com", RoleType.STUDENT);
        parent1 = createUser("p1_user", "p1@example.com", RoleType.PARENT);
        parent2 = createUser("p2_user", "p2@example.com", RoleType.PARENT);
        teacher1 = createUser("t1_user", "t1@example.com", RoleType.TEACHER);
        teacher2 = createUser("t2_user", "t2@example.com", RoleType.TEACHER);

        // 2. Link parent1 -> student1 ONLY
        parentChildLinkRepository.save(ParentChildLink.builder()
                .parentId(parent1.getId())
                .studentId(student1.getId())
                .build());

        // 3. Link teacher1 -> student1 ONLY
        teacherClassLinkRepository.save(TeacherClassLink.builder()
                .teacherId(teacher1.getId())
                .studentId(student1.getId())
                .build());

        // 4. Save SessionResult for student1
        sessionResultRepository.save(SessionResult.builder()
                .sessionId("sess-test-101")
                .studentId(student1.getId())
                .timestamp(LocalDateTime.now())
                .riskScore(0.85)
                .classification(SessionClassification.HIGH)
                .ruleScore(0.80)
                .rfScore(0.90)
                .build());

        // 5. Generate JWT tokens
        parent1Token = "Bearer " + jwtUtilService.generateToken(userDetailsService.loadUserByUsername("p1@example.com"));
        parent2Token = "Bearer " + jwtUtilService.generateToken(userDetailsService.loadUserByUsername("p2@example.com"));
        teacher1Token = "Bearer " + jwtUtilService.generateToken(userDetailsService.loadUserByUsername("t1@example.com"));
        teacher2Token = "Bearer " + jwtUtilService.generateToken(userDetailsService.loadUserByUsername("t2@example.com"));
        student1Token = "Bearer " + jwtUtilService.generateToken(userDetailsService.loadUserByUsername("s1@example.com"));
    }

    private User createUser(String username, String email, RoleType role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password123!"));
        u.setRoleType(role);
        u.setDateOfBirth(LocalDate.of(2012, 5, 10));
        u.setGender('M');
        u.setUserStatus(UserStatus.ACTIVE);
        u.setBlocked(false);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("Linked Parent gets 200 OK for child sessions, Unlinked Parent gets 403 Forbidden")
    void testParentChildSessionsAuthorization() throws Exception {
        // Linked parent -> 200 OK
        mockMvc.perform(get("/api/parents/me/children/" + student1.getId() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, parent1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("sess-test-101"))
                .andExpect(jsonPath("$[0].riskScore").value(0.85))
                .andExpect(jsonPath("$[0].classification").value("HIGH"))
                .andExpect(jsonPath("$[0].breakdown.ruleScore").value(0.80))
                .andExpect(jsonPath("$[0].breakdown.rfScore").value(0.90));

        // Unlinked parent -> 403 Forbidden
        mockMvc.perform(get("/api/parents/me/children/" + student1.getId() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, parent2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Linked Parent gets 200 OK & PDF for report, Unlinked Parent gets 403 Forbidden")
    void testParentChildPdfReportAuthorization() throws Exception {
        // Linked parent -> 200 OK with application/pdf
        mockMvc.perform(get("/api/parents/me/children/" + student1.getId() + "/report.pdf")
                        .header(HttpHeaders.AUTHORIZATION, parent1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Unlinked parent -> 403 Forbidden
        mockMvc.perform(get("/api/parents/me/children/" + student1.getId() + "/report.pdf")
                        .header(HttpHeaders.AUTHORIZATION, parent2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Linked Teacher gets 200 OK for student sessions, Unlinked Teacher gets 403 Forbidden")
    void testTeacherStudentSessionsAuthorization() throws Exception {
        // Linked teacher -> 200 OK
        mockMvc.perform(get("/api/teachers/me/students/" + student1.getId() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, teacher1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("sess-test-101"))
                .andExpect(jsonPath("$[0].riskScore").value(0.85))
                .andExpect(jsonPath("$[0].classification").value("HIGH"));

        // Unlinked teacher -> 403 Forbidden
        mockMvc.perform(get("/api/teachers/me/students/" + student1.getId() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, teacher2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student endpoints return correct contract format for authenticated student")
    void testStudentDashboardEndpoints() throws Exception {
        // Student sessions
        mockMvc.perform(get("/api/students/me/sessions")
                        .header(HttpHeaders.AUTHORIZATION, student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("sess-test-101"))
                .andExpect(jsonPath("$[0].riskScore").value(0.85))
                .andExpect(jsonPath("$[0].classification").value("HIGH"));

        // Student learning plan
        mockMvc.perform(get("/api/students/me/learning-plan")
                        .header(HttpHeaders.AUTHORIZATION, student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("HIGH"))
                .andExpect(jsonPath("$.language").value("en"));
    }
}
