package kz.genvibe.media_management.templates;

import kz.genvibe.media_management.model.entity.Organization;
import kz.genvibe.media_management.model.entity.Store;
import kz.genvibe.media_management.config.I18n;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorePlayerPageRenderingTest {

    @Test
    void rendersNewPlayer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(I18n.messageSource());

        var organization = Organization.builder().companyName("Test Cafe").build();
        ReflectionTestUtils.setField(organization, "id", 3L);
        var store = new Store();
        ReflectionTestUtils.setField(store, "id", 15L);

        var servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        var context = new WebContext(exchange);
        context.setVariables(Map.of(
            "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"),
            "store", store,
            "activeJingles", List.of(),
            "organization", organization
        ));

        var html = engine.process("pages/store-dashboard", context);

        assertTrue(html.contains("id=\"tapToStart\""), html);
        assertTrue(html.contains("id=\"playerStatus\""));
        assertTrue(html.contains("storeId: 15"), "player gets the store id");
        assertTrue(html.contains("src=\"/js/store-player.js\""));
        assertTrue(html.contains("function handlePauseRequest"));
        assertFalse(html.contains("mainAudio"), "old player script is gone");
    }

}
