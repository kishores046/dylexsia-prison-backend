package edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<@NonNull RefreshToken,@NonNull UUID> {

    List<RefreshToken> findByUser(User user);

    void deleteAllByUser(User user);

}