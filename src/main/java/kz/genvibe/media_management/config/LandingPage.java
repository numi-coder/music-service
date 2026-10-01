package kz.genvibe.media_management.config;

import kz.genvibe.media_management.model.entity.Store;
import kz.genvibe.media_management.model.enums.UserRole;
import kz.genvibe.media_management.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Where a user goes after signing in: company admins to their dashboard,
 * store accounts to their store's music player (they can't open the dashboard).
 */
@Component
@RequiredArgsConstructor
public class LandingPage {

    static final String NO_STORE = "/auth/login?noStore";

    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public String pathFor(String email, UserRole role) {
        if (role == UserRole.ROLE_ADMIN) return "/dashboard";

        return storeRepository.findByStoreUser_Email(email)
            .filter(store -> store.getMusicLinkUuid() != null)
            .map(LandingPage::playerPath)
            .orElse(NO_STORE);
    }

    /** Where a signed-in person belongs; empty for visitors who aren't signed in. */
    public Optional<String> pathFor(Authentication authentication) {
        if (!isSignedIn(authentication)) return Optional.empty();

        return Optional.of(pathFor(authentication.getName(), hasRole(authentication, UserRole.ROLE_ADMIN) ? UserRole.ROLE_ADMIN : UserRole.ROLE_USER));
    }

    public static boolean hasRole(Authentication authentication, UserRole role) {
        return authentication.getAuthorities().stream().anyMatch(a -> role.getAuthority().equals(a.getAuthority()));
    }

    public static boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static String playerPath(Store store) {
        return "/stores/" + store.getId() + "/" + store.getMusicLinkUuid();
    }

}
