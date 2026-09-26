package kz.genvibe.media_management.controller.user;

import kz.genvibe.media_management.config.LandingPage;
import kz.genvibe.media_management.controller.auth.PasswordSetupSession;
import kz.genvibe.media_management.model.domain.dto.user.PasswordSetupDto;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.enums.UserRole;
import kz.genvibe.media_management.service.internal.AuthService;
import kz.genvibe.media_management.service.internal.PasswordResetService;
import kz.genvibe.media_management.service.internal.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private LandingPage landingPage;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("Without a verified link in the session, no password is changed")
    void finalize_withoutSetupSession_changesNothing() {
        var request = new MockHttpServletRequest();
        var dto = new PasswordSetupDto("victim@example.com", "secret123", "secret123");

        var view = finalize(dto, request);

        assertEquals("redirect:/auth/login?setupExpired", view);
        verify(userService, never()).setPassword(anyLong(), anyString());
        verify(authService, never()).authenticate(any(), any(), any());
    }

    @Test
    @DisplayName("The email in the form is ignored; only the verified user's password changes")
    void finalize_usesVerifiedUserNotSubmittedEmail() {
        var verifiedUser = user(5L, "owner@example.com");
        var request = new MockHttpServletRequest();
        PasswordSetupSession.allow(request.getSession(), verifiedUser, false);
        when(userService.setPassword(5L, "secret123")).thenReturn(verifiedUser);
        when(landingPage.pathFor("owner@example.com", UserRole.ROLE_ADMIN)).thenReturn("/dashboard");

        var view = finalize(new PasswordSetupDto("victim@example.com", "secret123", "secret123"), request);

        assertEquals("redirect:/dashboard", view);
        verify(userService).setPassword(5L, "secret123");
        verify(passwordResetService).deleteTokens(verifiedUser);
        assertTrue(PasswordSetupSession.userId(request.getSession(false)).isEmpty(), "setup session is single use");
    }

    @Test
    @DisplayName("Mismatched passwords go back to the form without changing anything")
    void finalize_passwordMismatch() {
        var request = new MockHttpServletRequest();
        PasswordSetupSession.allow(request.getSession(), user(5L, "owner@example.com"), false);

        var view = finalize(new PasswordSetupDto(null, "secret123", "secret124"), request);

        assertEquals("redirect:/auth/register", view);
        verify(userService, never()).setPassword(anyLong(), anyString());
    }

    private String finalize(PasswordSetupDto dto, MockHttpServletRequest request) {
        return userController.setupUserPassword(
            dto,
            new BeanPropertyBindingResult(dto, "passwordSetupDto"),
            request,
            new MockHttpServletResponse(),
            new RedirectAttributesModelMap()
        );
    }

    private static AppUser user(long id, String email) {
        var appUser = AppUser.builder().email(email).role(UserRole.ROLE_ADMIN).build();
        ReflectionTestUtils.setField(appUser, "id", id);
        return appUser;
    }

}
