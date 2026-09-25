package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.Jingle;
import kz.genvibe.media_management.model.entity.Organization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JingleRepository extends JpaRepository<Jingle, Long> {
    @EntityGraph(attributePaths = "stores")
    List<Jingle> findJinglesByOrganization(Organization organization);

    @EntityGraph(attributePaths = "stores")
    List<Jingle> findJinglesByOrganizationAndRequestedToPauseIsTrue(Organization organization);

    Optional<Jingle> findJingleByIdAndOrganization(Long id, Organization organization);

    @EntityGraph(attributePaths = "stores")
    @Query("SELECT DISTINCT j FROM Jingle j JOIN j.stores WHERE j.startDate <= :endOfDay AND j.endDate >= :startOfDay")
    List<Jingle> findActiveAssignedJingles(
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );

    long countAllByOrganization(Organization organization);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM jingle_stores WHERE jingle_id = :id", nativeQuery = true)
    void deleteStoreLinks(@Param("id") long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Jingle j WHERE j.id = :id")
    void hardDeleteById(@Param("id") long id);
}
