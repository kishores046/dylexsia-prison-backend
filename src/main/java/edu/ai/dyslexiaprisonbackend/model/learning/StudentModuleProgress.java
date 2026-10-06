package edu.ai.dyslexiaprisonbackend.model.learning;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_module_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_module", columnNames = {"student_id", "module_template_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentModuleProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "module_template_id", nullable = false)
    private Long moduleTemplateId;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
