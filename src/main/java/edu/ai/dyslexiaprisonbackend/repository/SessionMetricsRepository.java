package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.metrics.SessionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * SessionMetricsRepository - Data access for SessionMetrics entities
 */
@Repository
public interface SessionMetricsRepository extends JpaRepository<SessionMetrics, Long> {
    
    Optional<SessionMetrics> findBySessionId(String sessionId);
    
    @Query("SELECT AVG(m.averageLatencyMs) FROM SessionMetrics m")
    Double getAverageLatency();
    
    @Query("SELECT MAX(m.maxLatencyMs) FROM SessionMetrics m")
    Double getMaxSystemLatency();
}

