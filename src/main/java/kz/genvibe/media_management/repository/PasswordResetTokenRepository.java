package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findTopByAppUserOrderByCreatedAtDesc(AppUser appUser);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.appUser = :appUser")
    void deleteAllByAppUser(@Param("appUser") AppUser appUser);
}
