package kz.genvibe.media_management.model.entity;

import jakarta.persistence.*;
import kz.genvibe.media_management.model.entity.base.CreateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken extends CreateEntity {

    // SHA-256 of the token sent by email; the token itself is never stored.
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "app_user_id", nullable = false, updatable = false)
    private AppUser appUser;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    public PasswordResetToken(String tokenHash, AppUser appUser, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.appUser = appUser;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

}
