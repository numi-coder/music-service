package kz.genvibe.media_management.model.entity;

import jakarta.persistence.*;
import kz.genvibe.media_management.model.entity.base.CreateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "jingle_generations")
@Getter
@NoArgsConstructor
public class JingleGeneration extends CreateEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    public JingleGeneration(Organization organization) {
        this.organization = organization;
    }

}
