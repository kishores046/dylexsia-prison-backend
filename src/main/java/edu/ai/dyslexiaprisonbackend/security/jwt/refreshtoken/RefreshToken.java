package edu.ai.dyslexiaprisonbackend.security.jwt.refreshtoken;
import edu.ai.dyslexiaprisonbackend.model.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false,length = 255)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Date expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    public boolean isExpired() {
        return new Date().after(this.expiresAt);
    }
}