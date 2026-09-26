package kz.genvibe.media_management.service.internal;

import kz.genvibe.media_management.model.entity.AppUser;

import java.util.Optional;

public interface PasswordResetService {
    void requestReset(String email);
    Optional<AppUser> findUserByValidToken(String token);
    void deleteTokens(AppUser appUser);
}
