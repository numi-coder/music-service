package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.LandingPage;
import kz.genvibe.media_management.model.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerTest {

    @Mock
    private LandingPage landingPage;

    @InjectMocks
    private AppController appController;

    @Test
    @DisplayName("The installed app sends signed-out people to the login page")
    void anonymousGoesToLogin() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertEquals("redirect:/auth/login", appController.openApp(anonymous));
        assertEquals("redirect:/auth/login", appController.openApp(null));
    }

    @Test
    @DisplayName("A store account opens its music player")
    void storeGoesToPlayer() {
        var store = new UsernamePasswordAuthenticationToken("store@example.com", null, List.of(UserRole.ROLE_USER));
        when(landingPage.pathFor("store@example.com", UserRole.ROLE_USER)).thenReturn("/stores/15/abc");

        assertEquals("redirect:/stores/15/abc", appController.openApp(store));
    }

    @Test
    @DisplayName("An owner opens the dashboard")
    void ownerGoesToDashboard() {
        var owner = new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of(UserRole.ROLE_ADMIN));
        when(landingPage.pathFor("owner@example.com", UserRole.ROLE_ADMIN)).thenReturn("/dashboard");

        assertEquals("redirect:/dashboard", appController.openApp(owner));
    }

}
