package edu.ai.dyslexiaprisonbackend.model.metrics;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SessionMetrics - Session-level performance metrics
 * 
 * Tracks:
 * - Frame processing statistics
 * - Network reliability
 * - Latency measurements
 */
@Entity
@Table(name = "session_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    private String sessionId;
    
    @Column(name = "frame_count", nullable = false)
    @Builder.Default
    private Integer frameCount = 0;
    
    @Column(name = "feature_count", nullable = false)
    @Builder.Default
    private Integer featureCount = 0;
    
    @Column(name = "dropped_frames", nullable = false)
    @Builder.Default
    private Integer droppedFrames = 0;
    
    @Column(name = "average_latency_ms")
    private Double averageLatencyMs;
    
    @Column(name = "max_latency_ms")
    private Double maxLatencyMs;
    
    @Column(name = "min_latency_ms")
    private Double minLatencyMs;
    
    @Column(name = "websocket_disconnects", nullable = false)
    @Builder.Default
    private Integer websocketDisconnects = 0;
    
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

