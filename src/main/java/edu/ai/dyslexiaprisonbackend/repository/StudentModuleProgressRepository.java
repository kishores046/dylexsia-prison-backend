package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.learning.StudentModuleProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentModuleProgressRepository extends JpaRepository<StudentModuleProgress, Long> {

    Optional<StudentModuleProgress> findByStudentIdAndModuleTemplateId(Long studentId, Long moduleTemplateId);

    List<StudentModuleProgress> findByStudentId(Long studentId);
}
