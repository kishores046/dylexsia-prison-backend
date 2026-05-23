package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.session.GazeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GazeSessionRepository - Data access for GazeSession entities
 */
@Repository
public interface GazeSessionRepository extends JpaRepository<GazeSession, Long> {
    
    Optional<GazeSession> findBySessionId(String sessionId);
    
    List<GazeSession> findByUsername(String username);
    
    List<GazeSession> findByTaskId(String taskId);
    
    List<GazeSession> findBySessionStatus(String status);
    
    @Query("SELECT g FROM GazeSession g WHERE g.username = :username AND g.createdAt >= :since ORDER BY g.createdAt DESC")
    List<GazeSession> findUserSessionsSince(@Param("username") String username, @Param("since") LocalDateTime since);
    
    @Query("SELECT g FROM GazeSession g WHERE g.sessionStatus = 'active' ORDER BY g.startedAt ASC")
    List<GazeSession> findActiveSessions();
    
    @Query("SELECT COUNT(g) FROM GazeSession g WHERE g.username = :username AND g.sessionStatus = 'active'")
    long countActiveSessionsForUser(@Param("username") String username);
}

