package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "student_link_codes",
        indexes = {
                @Index(
                        name = "idx_student_link_code",
                        columnList = "code"
                ),
                @Index(
                        name = "idx_student_link_student",
                        columnList = "student_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLinkCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Student who owns this code.
     */
    @Column(
            name = "student_id",
            nullable = false
    )
    private Long studentId;

    /**
     * Human-friendly code shared with parent/teacher.
     */
    @Column(
            nullable = false,
            unique = true,
            length = 30
    )
    private String code;

    /**
     * Determines who can consume this code.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private LinkType linkType;

    /**
     * Whether this code can still be used.
     */
    @Column(
            nullable = false
    )
    private boolean active;

    /**
     * Expiration time.
     */
    @Column(
            nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(
            nullable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}