package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.LandingPage;
import kz.genvibe.media_management.model.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Where the installed app opens: the store's music player for store accounts,
 * the dashboard for owners, the login page for everyone else.
 */
@Controller
@RequiredArgsConstructor
public class AppController {

    private final LandingPage landingPage;

    @GetMapping("/player")
    public String openApp(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/auth/login";
        }

        var role = authentication.getAuthorities().stream()
            .filter(UserRole.class::isInstance)
            .map(UserRole.class::cast)
            .findFirst()
            .orElse(UserRole.ROLE_USER);

        return "redirect:" + landingPage.pathFor(authentication.getName(), role);
    }

}
