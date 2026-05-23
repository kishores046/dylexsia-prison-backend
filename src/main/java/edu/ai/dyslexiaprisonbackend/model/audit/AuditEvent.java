package edu.ai.dyslexiaprisonbackend.model.audit;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AuditEvent - Audit logging for sessions
 * 
 * Tracks:
 * - Session lifecycle events
 * - Error conditions
 * - System events
 */
@Entity
@Table(name = "audit_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // "session_start", "frame_received", "aggregation", "ml_request", "ml_response", "session_end", "error", etc
    
    @Column(name = "event_message", columnDefinition = "TEXT")
    private String eventMessage;
    
    @Column(name = "severity", nullable = false, length = 20)
    private String severity; // "info", "warning", "error"
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}

