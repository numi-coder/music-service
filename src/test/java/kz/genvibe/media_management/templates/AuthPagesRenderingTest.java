package kz.genvibe.media_management.templates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPagesRenderingTest {

    private SpringTemplateEngine engine;

    @BeforeEach
    void setUp() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");

        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    @Test
    void loginShowsErrorAndForgotLink() {
        var html = render("pages/auth/login", Map.of("error", ""), Map.of());

        assertTrue(html.contains("<title>Sign in</title>"), html);
        assertTrue(html.contains("Wrong email or password"));
        assertTrue(html.contains("href=\"/auth/forgot-password\""));
        assertFalse(html.contains("pattern="), "login must not validate password format");
    }

    @Test
    void forgotPasswordSentHidesForm() {
        var html = render("pages/auth/forgot-password", Map.of("sent", ""), Map.of());

        assertTrue(html.contains("If an account exists for that email"));
        assertFalse(html.contains("Send reset link"));
    }

    @Test
    void forgotPasswordShowsForm() {
        var html = render("pages/auth/forgot-password", Map.of(), Map.of());

        assertTrue(html.contains("Send reset link"));
        assertTrue(html.contains("action=\"/auth/forgot-password\""));
    }

    @Test
    void setPasswordPageShowsEmailReadOnlyAndDoesNotSubmitIt() {
        var html = render("pages/auth/register", Map.of(), Map.of("email", "owner@example.com", "isReset", true));

        assertTrue(html.contains("Choose a new password"));
        assertTrue(html.contains("value=\"owner@example.com\" readonly"), html);
        assertFalse(html.contains("name=\"email\""), "the email must not be posted with the form");
        assertTrue(html.contains("Save new password"));
    }

    @Test
    void resetEmailContainsLink() {
        var context = new Context();
        context.setVariable("resetUrl", "https://weresona.com/auth/reset-password?token=abc");

        var html = engine.process("pages/email/reset-password", context);

        assertTrue(html.contains("href=\"https://weresona.com/auth/reset-password?token=abc\""));
        assertTrue(html.contains("works for 1 hour"));
    }

    private String render(String template, Map<String, String> params, Map<String, Object> model) {
        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        params.forEach(request::addParameter);
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(request, new MockHttpServletResponse());

        var context = new WebContext(exchange);
        context.setVariables(model);
        return engine.process(template, context);
    }

}
