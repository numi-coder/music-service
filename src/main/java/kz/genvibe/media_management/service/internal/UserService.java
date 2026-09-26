package kz.genvibe.media_management.service.internal;

import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.domain.dto.user.AppUserUpdateDto;
import kz.genvibe.media_management.model.entity.Organization;

public interface UserService {
    // User modification methods
    AppUser setPassword(long userId, String rawPassword);
    void updateUser(AppUserUpdateDto dto, AppUser appUser);
    AppUser createStoreUser(String email, Organization organization);

    // User read methods
    AppUser getUserByEmail(String email);
    AppUser getUserById(long id);
    boolean existsByEmail(String email);
}
