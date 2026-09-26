package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.JingleGeneration;
import kz.genvibe.media_management.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface JingleGenerationRepository extends JpaRepository<JingleGeneration, Long> {
    long countAllByOrganizationAndCreatedAtGreaterThanEqual(Organization organization, Instant since);
}
