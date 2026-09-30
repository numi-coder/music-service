package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.MusicGenerationJob;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MusicGenerationJobRepository extends JpaRepository<MusicGenerationJob, Long> {

    Optional<MusicGenerationJob> findFirstByStatusOrderByIdAsc(MusicJobStatus status);

    List<MusicGenerationJob> findAllByStatusAndStartedAtBefore(MusicJobStatus status, Instant before);

    List<MusicGenerationJob> findTop200ByOrderByIdDesc();

    long countByStatus(MusicJobStatus status);

    long countByAtmosphere(MusicAtmosphere atmosphere);

    /** What generation has cost since the given moment; null when nothing was generated. */
    @Query("select sum(j.costUsd) from MusicGenerationJob j where j.startedAt >= :since")
    BigDecimal sumCostSince(@Param("since") Instant since);

}
