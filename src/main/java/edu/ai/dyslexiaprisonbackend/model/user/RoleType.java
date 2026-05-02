package edu.ai.dyslexiaprisonbackend.model.user;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RoleType {
    STUDENT("STUDENT"),
    ADMIN("ADMIN"),
    TEACHER("TEACHER"),
    PARENT("PARENT");

    private final String role;
    RoleType(String role) {
        this.role=role;
    }

    @JsonValue
    public String getRole(){
        return role;
    }
}
