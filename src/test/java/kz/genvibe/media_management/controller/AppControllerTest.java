package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.LandingPage;
import kz.genvibe.media_management.config.SignedInRedirect;
import kz.genvibe.media_management.model.enums.UserRole;
import kz.genvibe.media_management.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppControllerTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final LandingPage landingPage = new LandingPage(storeRepository);
    private final AppController appController = new AppController(landingPage);

    private static final Authentication VISITOR = new AnonymousAuthenticationToken("key", "anonymousUser",
        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    private static final Authentication OWNER = new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of(UserRole.ROLE_ADMIN));
    // After a restart, roles come back from the session store as plain authorities.
    private static final Authentication OWNER_PLAIN = new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    private static final Authentication STORE_WITHOUT_LINK = new UsernamePasswordAuthenticationToken("store@example.com", null, List.of(UserRole.ROLE_USER));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("weresona.com shows the landing page to visitors and the dashboard to signed-in owners")
    void siteRoot() {
        assertEquals("pages/landing", appController.home(VISITOR));
        assertEquals("pages/landing", appController.home(null));
        assertEquals("redirect:/dashboard", appController.home(OWNER));
        assertEquals("redirect:/dashboard", appController.home(OWNER_PLAIN));
    }

    @Test
    @DisplayName("The installed app opens the login page for visitors and the right page for accounts")
    void installedApp() {
        when(storeRepository.findByStoreUser_Email("store@example.com")).thenReturn(Optional.empty());

        assertEquals("redirect:/auth/login", appController.openApp(VISITOR));
        assertEquals("redirect:/dashboard", appController.openApp(OWNER));
        assertEquals("redirect:/auth/login?noStore", appController.openApp(STORE_WITHOUT_LINK));
    }

    @Test
    @DisplayName("Signed-in people skip sign-up and login; visitors and stores without a store don't loop")
    void signedInRedirect() throws Exception {
        when(storeRepository.findByStoreUser_Email("store@example.com")).thenReturn(Optional.empty());
        var interceptor = new SignedInRedirect(landingPage);

        SecurityContextHolder.getContext().setAuthentication(OWNER);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", "/onboarding/welcome"), response, new Object()));
        assertEquals("/dashboard", response.getRedirectedUrl());

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/auth/login"), new MockHttpServletResponse(), new Object()),
            "posting the login form still works");

        SecurityContextHolder.getContext().setAuthentication(STORE_WITHOUT_LINK);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/auth/login"), new MockHttpServletResponse(), new Object()));

        SecurityContextHolder.getContext().setAuthentication(VISITOR);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/onboarding/welcome"), new MockHttpServletResponse(), new Object()));
    }

}
