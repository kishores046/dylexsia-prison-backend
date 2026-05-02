package edu.ai.dyslexiaprisonbackend.repository;


import edu.ai.dyslexiaprisonbackend.model.user.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<@NonNull User,@NonNull Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
