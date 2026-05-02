package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Parent {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Embedded
    private Address address;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParentStudent> studentLinks = new ArrayList<>();

    public void addStudent(Student student, String relationType) {
        ParentStudent link = new ParentStudent();
        link.setParent(this);
        link.setStudent(student);
        link.setRelationshipType(relationType);

        this.studentLinks.add(link);
        student.getParentLinks().add(link);
    }


}
