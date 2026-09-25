package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.JingleSlot;
import kz.genvibe.media_management.model.enums.JingleSlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JingleSlotRepository extends JpaRepository<JingleSlot, Long> {
    List<JingleSlot> findJingleSlotsByPlayTimeLessThanEqualAndStatusAndJingleRequestedToPauseIsFalse(
        Instant playTime,
        JingleSlotStatus status
    );

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM JingleSlot s WHERE s.jingle.id = :jingleId")
    void deleteByJingleId(@Param("jingleId") long jingleId);
}
