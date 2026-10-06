package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.user.ParentChildLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, Long> {

    List<ParentChildLink> findByParentId(Long parentId);

    boolean existsByParentIdAndStudentId(Long parentId, Long studentId);
}
