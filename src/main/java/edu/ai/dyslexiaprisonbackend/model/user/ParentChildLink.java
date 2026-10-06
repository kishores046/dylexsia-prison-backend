package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parent_child_links", indexes = {
        @Index(name = "idx_pcl_parent", columnList = "parent_id"),
        @Index(name = "idx_pcl_student", columnList = "student_id")
},
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_parent_student",
                        columnNames = {
                                "parent_id",
                                "student_id"
                        }
                )})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentChildLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;
}
