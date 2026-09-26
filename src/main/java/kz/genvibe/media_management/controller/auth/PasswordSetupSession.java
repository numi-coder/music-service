package kz.genvibe.media_management.controller.auth;

import jakarta.servlet.http.HttpSession;
import kz.genvibe.media_management.model.entity.AppUser;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Remembers, in the HTTP session, which user just proved they own their email
 * (verification link or password reset link) and may therefore set a password.
 * The password setup form can only change that user's password.
 */
public final class PasswordSetupSession {

    private static final String USER_ID = "passwordSetup.userId";
    private static final String EXPIRES_AT = "passwordSetup.expiresAt";
    private static final String IS_RESET = "passwordSetup.isReset";
    private static final Duration VALIDITY = Duration.ofMinutes(30);

    private PasswordSetupSession() {
    }

    public static void allow(HttpSession session, AppUser appUser, boolean isReset) {
        session.setAttribute(USER_ID, appUser.getId());
        session.setAttribute(EXPIRES_AT, Instant.now().plus(VALIDITY));
        session.setAttribute(IS_RESET, isReset);
    }

    public static Optional<Long> userId(HttpSession session) {
        if (session == null) return Optional.empty();
        if (!(session.getAttribute(USER_ID) instanceof Long userId)) return Optional.empty();
        if (!(session.getAttribute(EXPIRES_AT) instanceof Instant expiresAt) || Instant.now().isAfter(expiresAt)) {
            clear(session);
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    public static boolean isReset(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(IS_RESET));
    }

    public static void clear(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(USER_ID);
        session.removeAttribute(EXPIRES_AT);
        session.removeAttribute(IS_RESET);
    }

}
