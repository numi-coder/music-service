package kz.genvibe.media_management.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Someone who is already signed in never sees the sign-up questions or the login
 * form again: opening them takes them to their dashboard (or store player).
 */
@Component
@RequiredArgsConstructor
public class SignedInRedirect implements HandlerInterceptor {

    static final String[] PATHS = {"/onboarding/**", "/auth/login"};

    private final LandingPage landingPage;

    @Override
    public boolean preHandle(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler
    ) throws IOException {
        if (!"GET".equals(request.getMethod())) return true;

        var target = landingPage.pathFor(SecurityContextHolder.getContext().getAuthentication());
        // A store account without an active store is sent to the login page; don't loop.
        if (target.isEmpty() || target.get().startsWith("/auth/login")) return true;

        response.sendRedirect(request.getContextPath() + target.get());
        return false;
    }

}
