package kz.genvibe.media_management.config;

import kz.genvibe.media_management.model.entity.Store;
import kz.genvibe.media_management.model.enums.UserRole;
import kz.genvibe.media_management.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where a user goes after signing in: company admins to their dashboard,
 * store accounts to their store's music player (they can't open the dashboard).
 */
@Component
@RequiredArgsConstructor
public class LandingPage {

    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public String pathFor(String email, UserRole role) {
        if (role == UserRole.ROLE_ADMIN) return "/dashboard";

        return storeRepository.findByStoreUser_Email(email)
            .filter(store -> store.getMusicLinkUuid() != null)
            .map(LandingPage::playerPath)
            .orElse("/auth/login?noStore");
    }

    private static String playerPath(Store store) {
        return "/stores/" + store.getId() + "/" + store.getMusicLinkUuid();
    }

}
