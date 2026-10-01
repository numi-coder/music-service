package kz.genvibe.media_management.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kz.genvibe.media_management.model.entity.base.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A request left through the form on the public landing page. */
@Entity
@Table(name = "landing_requests")
@Getter
@NoArgsConstructor
public class LandingRequest extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 160)
    private String companyName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 8)
    private String language;

    public LandingRequest(String companyName, String email, String language) {
        this.createdAt = Instant.now();
        this.companyName = companyName;
        this.email = email;
        this.language = language;
    }

}
