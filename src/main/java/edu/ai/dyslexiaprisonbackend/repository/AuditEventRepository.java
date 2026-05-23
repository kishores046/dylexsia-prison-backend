package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AuditEventRepository - Data access for AuditEvent entities
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    
    List<AuditEvent> findBySessionId(String sessionId);
    
    List<AuditEvent> findByEventType(String eventType);
    
    List<AuditEvent> findBySeverity(String severity);
    
    @Query("SELECT a FROM AuditEvent a WHERE a.sessionId = :sessionId ORDER BY a.timestamp DESC")
    List<AuditEvent> findSessionAuditTrail(@Param("sessionId") String sessionId);
    
    @Query("SELECT a FROM AuditEvent a WHERE a.severity = 'error' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditEvent> findRecentErrors(@Param("since") LocalDateTime since);
}

