package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.result.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionResultRepository extends JpaRepository<SessionResult, Long> {

    List<SessionResult> findByStudentIdOrderByTimestampDesc(Long studentId);

    Optional<SessionResult> findFirstByStudentIdOrderByTimestampDesc(Long studentId);
}
