package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.result.MlResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MlResultRepository - Data access for MlResult entities
 */
@Repository
public interface MlResultRepository extends JpaRepository<MlResult, Long> {
    
    Optional<MlResult> findBySessionId(String sessionId);
    
    List<MlResult> findByClassification(String classification);
    
    @Query("SELECT m FROM MlResult m WHERE m.riskScore >= :minRisk ORDER BY m.riskScore DESC")
    List<MlResult> findHighRiskResults(@Param("minRisk") Double minRisk);
    
    @Query("SELECT m FROM MlResult m WHERE m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<MlResult> findResultsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(m.confidence) FROM MlResult m")
    Double getAverageConfidence();
}

