package kz.genvibe.media_management.service.internal.impl;

import kz.genvibe.media_management.config.props.AppProps;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.entity.PasswordResetToken;
import kz.genvibe.media_management.repository.AppUserRepository;
import kz.genvibe.media_management.repository.PasswordResetTokenRepository;
import kz.genvibe.media_management.service.internal.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private AppProps appProps;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    @Test
    @DisplayName("Unknown email: nothing is stored or sent")
    void requestReset_unknownEmail() {
        when(appUserRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("nobody@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendHtmlMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Unverified account: nothing is sent")
    void requestReset_unverifiedAccount() {
        var appUser = AppUser.builder().email("new@example.com").emailVerified(false).build();
        when(appUserRepository.findByEmail("new@example.com")).thenReturn(Optional.of(appUser));

        passwordResetService.requestReset("new@example.com");

        verify(mailService, never()).sendHtmlMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Verified account: only the token's hash is stored, the raw token is emailed")
    void requestReset_storesHashAndEmailsToken() {
        var appUser = AppUser.builder().email("owner@example.com").emailVerified(true).build();
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(appUser));
        when(passwordResetTokenRepository.findTopByAppUserOrderByCreatedAtDesc(appUser)).thenReturn(Optional.empty());
        when(appProps.getBaseUrl()).thenReturn("https://weresona.com");
        when(templateEngine.process(eq("pages/email/reset-password"), any(Context.class))).thenReturn("<html/>");

        passwordResetService.requestReset(" owner@example.com ");

        var tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        var contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("pages/email/reset-password"), contextCaptor.capture());
        verify(mailService).sendHtmlMail(eq("owner@example.com"), anyString(), eq("<html/>"));

        var resetUrl = (String) contextCaptor.getValue().getVariable("resetUrl");
        assertTrue(resetUrl.startsWith("https://weresona.com/auth/reset-password?token="));
        var rawToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);

        var saved = tokenCaptor.getValue();
        assertNotEquals(rawToken, saved.getTokenHash());
        assertEquals(PasswordResetServiceImpl.hash(rawToken), saved.getTokenHash());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plusSeconds(3500)));
    }

    @Test
    @DisplayName("A second request within a minute sends nothing")
    void requestReset_cooldown() {
        var appUser = AppUser.builder().email("owner@example.com").emailVerified(true).build();
        var recent = new PasswordResetToken("h", appUser, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(recent, "createdAt", Instant.now().minusSeconds(10));
        when(appUserRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(appUser));
        when(passwordResetTokenRepository.findTopByAppUserOrderByCreatedAtDesc(appUser)).thenReturn(Optional.of(recent));

        passwordResetService.requestReset("owner@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendHtmlMail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Expired tokens don't work; valid ones return their user")
    void findUserByValidToken() {
        var appUser = AppUser.builder().email("owner@example.com").build();
        when(passwordResetTokenRepository.findByTokenHash(PasswordResetServiceImpl.hash("expired")))
            .thenReturn(Optional.of(new PasswordResetToken("x", appUser, Instant.now().minusSeconds(1))));
        when(passwordResetTokenRepository.findByTokenHash(PasswordResetServiceImpl.hash("valid")))
            .thenReturn(Optional.of(new PasswordResetToken("y", appUser, Instant.now().plusSeconds(60))));

        assertTrue(passwordResetService.findUserByValidToken("expired").isEmpty());
        assertTrue(passwordResetService.findUserByValidToken("").isEmpty());
        assertEquals(appUser, passwordResetService.findUserByValidToken("valid").orElseThrow());
    }

}
