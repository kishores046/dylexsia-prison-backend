package edu.ai.dyslexiaprisonbackend.model.user;


import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_table")
@Getter
@Setter
@NoArgsConstructor
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_name",nullable = false)
    private String username;

    @Column(name = "email_id",unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private RoleType roleType;


    @Column(name = "password",nullable = false)
    private String password;

    @Column(name="dob",nullable = false)
    private LocalDate dateOfBirth;

    @Column(name="gender")
    private char gender;

    @Column(name = "status",nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus userStatus;

    @Column(name = "ph_no")
    private String phoneNo;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_by")
    private String blockedBy;

    @Column(name = "block_reason")
    private String blockReason;



    @Column(nullable = false,name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false,name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist(){
        this.createdAt= LocalDateTime.now();
        this.updatedAt=LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate(){
        this.updatedAt=LocalDateTime.now();
    }

}

