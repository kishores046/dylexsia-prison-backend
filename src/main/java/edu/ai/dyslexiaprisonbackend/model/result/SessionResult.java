package edu.ai.dyslexiaprisonbackend.model.result;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false)
    private SessionClassification classification;

    @Column(name = "rule_score")
    private Double ruleScore;

    @Column(name = "rf_score")
    private Double rfScore;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
