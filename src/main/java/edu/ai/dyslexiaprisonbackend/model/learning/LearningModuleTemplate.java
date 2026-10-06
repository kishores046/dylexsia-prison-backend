package edu.ai.dyslexiaprisonbackend.model.learning;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "learning_module_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningModuleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_tier", nullable = false)
    private String riskTier;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "order_index")
    private Integer orderIndex;
}
