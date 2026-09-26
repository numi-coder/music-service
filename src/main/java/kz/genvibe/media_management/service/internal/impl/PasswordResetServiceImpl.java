package kz.genvibe.media_management.service.internal.impl;

import kz.genvibe.media_management.config.props.AppProps;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.entity.PasswordResetToken;
import kz.genvibe.media_management.repository.AppUserRepository;
import kz.genvibe.media_management.repository.PasswordResetTokenRepository;
import kz.genvibe.media_management.service.internal.MailService;
import kz.genvibe.media_management.service.internal.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    static final Duration TOKEN_VALIDITY = Duration.ofHours(1);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final String RESET_URL_PATH = "/auth/reset-password?token=";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final TemplateEngine templateEngine;
    private final AppProps appProps;

    /**
     * Emails a reset link if the email belongs to an active, verified account.
     * Callers must show the same response either way, so the form can't be used
     * to find out which emails have accounts.
     */
    @Override
    @Transactional
    public void requestReset(String email) {
        var appUser = appUserRepository.findByEmail(email.trim())
            .filter(AppUser::isEnabled)
            .filter(AppUser::isEmailVerified)
            .orElse(null);

        if (appUser == null) {
            log.info("Password reset requested for an email without an active account");
            return;
        }

        var recentlySent = passwordResetTokenRepository.findTopByAppUserOrderByCreatedAtDesc(appUser)
            .filter(t -> t.getCreatedAt().isAfter(Instant.now().minus(RESEND_COOLDOWN)))
            .isPresent();
        if (recentlySent) {
            log.info("Password reset for user {} skipped: a link was sent less than a minute ago", appUser.getId());
            return;
        }

        passwordResetTokenRepository.deleteAllByAppUser(appUser);

        var token = newToken();
        passwordResetTokenRepository.save(
            new PasswordResetToken(hash(token), appUser, Instant.now().plus(TOKEN_VALIDITY))
        );

        var context = new Context();
        context.setVariable("resetUrl", appProps.getBaseUrl() + RESET_URL_PATH + token);
        var html = templateEngine.process("pages/email/reset-password", context);
        mailService.sendHtmlMail(appUser.getEmail(), "Reset your Resona AI password", html);

        log.info("Password reset link sent to user {}", appUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppUser> findUserByValidToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        return passwordResetTokenRepository.findByTokenHash(hash(token))
            .filter(t -> !t.isExpired())
            .map(PasswordResetToken::getAppUser)
            .filter(AppUser::isEnabled);
    }

    @Override
    @Transactional
    public void deleteTokens(AppUser appUser) {
        passwordResetTokenRepository.deleteAllByAppUser(appUser);
    }

    static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
