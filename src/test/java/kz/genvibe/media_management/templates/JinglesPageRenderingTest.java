package kz.genvibe.media_management.templates;

import kz.genvibe.media_management.model.entity.Jingle;
import kz.genvibe.media_management.model.entity.Organization;
import kz.genvibe.media_management.model.enums.JingleCategory;
import kz.genvibe.media_management.model.enums.JingleRepeatingTime;
import kz.genvibe.media_management.model.enums.JingleVoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JinglesPageRenderingTest {

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
    void rendersUsageEditButtonAndVoiceSamples() {
        var html = render(7, null);

        assertTrue(html.contains("Monthly generation limit: 7 of 20 used this month"), html);
        assertTrue(html.contains("data-voice=\"RACHEL\""));
        assertTrue(html.contains("class=\"btn-edit-schedule\""));
        assertTrue(html.contains("data-start=\"2026-10-01T09:30\""));
        assertTrue(html.contains("data-repeat=\"EVERY_HOUR\""));
        assertTrue(html.contains("id=\"scheduleModal\""));
        assertFalse(html.contains("disabled=\"disabled\" title=\"Monthly limit reached\""));
    }

    @Test
    void atLimitDisablesGenerateAndShowsMessage() {
        var html = render(20, "You’ve used all 20 jingles for this month. The limit resets on 1 October.");

        assertTrue(html.contains("Monthly limit reached"), html);
        assertTrue(html.contains("The limit resets on 1 October."));
    }

    private String render(long used, String jingleError) {
        var jingle = Jingle.builder()
            .voice(JingleVoice.RACHEL)
            .category(JingleCategory.PROMOTIONAL)
            .startDate(LocalDateTime.of(2026, 10, 1, 9, 30))
            .endDate(LocalDateTime.of(2026, 10, 2, 9, 30))
            .repeatingTime(JingleRepeatingTime.EVERY_HOUR)
            .announcementText("Welcome!")
            .fileUrl("https://weresona.com/files/a.mp3")
            .build();
        ReflectionTestUtils.setField(jingle, "id", 18L);

        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(request, new MockHttpServletResponse());

        var context = new WebContext(exchange);
        context.setVariables(Map.of(
            "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"),
            "jingleVoice", JingleVoice.values(),
            "jingleCategory", JingleCategory.values(),
            "jingleRepeatingTime", JingleRepeatingTime.values(),
            "jingleHistory", List.of(jingle),
            "jinglesUsedThisMonth", used,
            "monthlyJingleLimit", 20,
            "jingleRequestsToPause", List.of(),
            "activeStores", List.of(),
            "organization", Organization.builder().companyName("Test Cafe").build()
        ));
        if (jingleError != null) context.setVariable("jingleError", jingleError);

        return engine.process("pages/jingles", context);
    }

}
