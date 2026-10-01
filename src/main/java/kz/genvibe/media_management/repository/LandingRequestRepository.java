package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.LandingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingRequestRepository extends JpaRepository<LandingRequest, Long> {
}
