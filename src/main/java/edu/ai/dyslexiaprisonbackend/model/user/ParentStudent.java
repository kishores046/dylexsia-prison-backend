package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import lombok.Setter;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_parent_id", columnList = "parent_id"),
                @Index(name = "idx_student_id", columnList = "student_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ParentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    private String relationshipType;
    private boolean primaryContact;
    private int emergencyPriority;
}
