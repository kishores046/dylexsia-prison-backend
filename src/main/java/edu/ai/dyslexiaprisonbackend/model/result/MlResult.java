package edu.ai.dyslexiaprisonbackend.model.result;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * MlResult - Persistent storage for ML analysis results
 * 
 * Stores:
 * - Risk score and classification
 * - Confidence metrics
 * - Rule-based and Random Forest scores
 * - Processing time
 */
@Entity
@Table(name = "ml_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    
    @Column(name = "risk_score", nullable = false)
    private Double riskScore; // 0.0 - 1.0
    
    @Column(name = "classification", nullable = false, length = 100)
    private String classification; // "no_risk", "mild_risk", "moderate_risk", "high_risk"
    
    @Column(name = "confidence", nullable = false)
    private Double confidence; // 0.0 - 1.0
    
    @Column(name = "rule_score")
    private Double ruleScore;
    
    @Column(name = "rf_score")
    private Double rfScore;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

