package edu.ai.dyslexiaprisonbackend.model.session;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GazeSession - Persistent storage for gaze analysis sessions
 * 
 * Stores complete session lifecycle:
 * - Session metadata (id, user, task)
 * - Timestamps (start, end)
 * - Status tracking
 * - Device information
 * - Statistics (frame count, latency)
 */
@Entity
@Table(name = "gaze_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GazeSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    private String sessionId;
    
    @Column(name = "username", nullable = false, length = 255)
    private String username;
    
    @Column(name = "task_id", nullable = false, length = 255)
    private String taskId;
    
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "session_status", nullable = false, length = 50)
    private String sessionStatus; // "active", "completed", "failed"
    
    @Column(name = "device_metadata", columnDefinition = "TEXT")
    private String deviceMetadata;
    
    @Column(name = "frame_count", nullable = false)
    @Builder.Default
    private Integer frameCount = 0;
    
    @Column(name = "feature_count", nullable = false)
    @Builder.Default
    private Integer featureCount = 0;
    
    @Column(name = "average_latency_ms")
    private Double averageLatencyMs;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

