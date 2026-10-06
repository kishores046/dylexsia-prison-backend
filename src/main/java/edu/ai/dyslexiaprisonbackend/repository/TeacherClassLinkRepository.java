package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.user.TeacherClassLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherClassLinkRepository extends JpaRepository<TeacherClassLink, Long> {

    List<TeacherClassLink> findByTeacherId(Long teacherId);

    boolean existsByTeacherIdAndStudentId(Long teacherId, Long studentId);
}
