package kz.genvibe.media_management.templates;

import kz.genvibe.media_management.config.I18n;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingPageRenderingTest {

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru"})
    void rendersBothLanguages(String language) throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(I18n.messageSource());

        var servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        var html = engine.process("pages/landing", new WebContext(exchange, Locale.forLanguageTag(language)));

        var snapshots = System.getenv("RESONA_SNAPSHOTS");
        if (snapshots != null) Files.writeString(Path.of(snapshots, "landing-" + language + ".html"), html);

        assertFalse(html.contains("??"), "a text is missing from the message files");
        assertTrue(html.contains("href=\"/onboarding/welcome\""));
        assertTrue(html.contains("href=\"/auth/login\""));
        assertTrue(html.contains("action=\"/landing/request\""));
        assertTrue(html.contains("data-audio=\"/assets/landing/jingle-2.mp3\""));
        assertTrue(html.contains("id=\"faq-a-5\""));
        if (language.equals("ru")) {
            assertTrue(html.contains("Создавайте атмосферу."), html);
            assertTrue(html.contains("<html lang=\"ru\""));
        } else {
            assertTrue(html.contains("Design the atmosphere."), html);
            assertTrue(html.contains("Who handles the music licensing"));
        }
    }

}
