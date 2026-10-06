package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher_class_links", indexes = {
        @Index(name = "idx_tcl_teacher", columnList = "teacher_id"),
        @Index(name = "idx_tcl_student", columnList = "student_id")
},
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_teacher_student",
                        columnNames = {
                                "teacher_id",
                                "student_id"
                        }
                )
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherClassLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;
}
