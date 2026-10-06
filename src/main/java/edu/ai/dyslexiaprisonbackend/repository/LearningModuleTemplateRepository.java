package edu.ai.dyslexiaprisonbackend.repository;

import edu.ai.dyslexiaprisonbackend.model.learning.LearningModuleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningModuleTemplateRepository extends JpaRepository<LearningModuleTemplate, Long> {

    List<LearningModuleTemplate> findByRiskTierOrderByOrderIndexAsc(String riskTier);
}
