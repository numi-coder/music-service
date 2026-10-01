package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.LandingPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The two front doors. weresona.com shows the landing page to visitors and sends
 * signed-in people straight to their dashboard (stores to their music player).
 * The installed app does the same, but sends visitors to the login page.
 */
@Controller
@RequiredArgsConstructor
public class AppController {

    private final LandingPage landingPage;

    @GetMapping("/")
    public String home(Authentication authentication) {
        return landingPage.pathFor(authentication)
            .map(path -> "redirect:" + path)
            .orElse("pages/landing");
    }

    @GetMapping("/player")
    public String openApp(Authentication authentication) {
        return "redirect:" + landingPage.pathFor(authentication).orElse("/auth/login");
    }

}
