package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.user.LinkType;
import edu.ai.dyslexiaprisonbackend.model.user.StudentLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentLinkCodeRepository
        extends JpaRepository<StudentLinkCode, Long> {

    Optional<StudentLinkCode>
    findByCodeAndActiveTrue(String code);

    Optional<StudentLinkCode>
    findByStudentIdAndLinkTypeAndActiveTrue(
            Long studentId,
            LinkType linkType
    );

    List<StudentLinkCode>
    findByStudentId(Long studentId);
}