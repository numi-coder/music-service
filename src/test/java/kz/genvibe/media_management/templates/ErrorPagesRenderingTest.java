package kz.genvibe.media_management.templates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorPagesRenderingTest {

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

    @ParameterizedTest
    @CsvSource({
        "error/403, 403, /stores/15/abc, store’s own player",
        "error/403, 403, /settings, don’t have access",
        "error/404, 404, /nope, Page not found",
        "error/409, , , Account already exists",
        "error/410, , , Link expired",
        "error/4xx, 400, /x, Something went wrong",
        "error/500, 500, /x, Something went wrong",
        "error/error, 502, /x, Please try again in a moment"
    })
    void rendersFriendlyErrorPage(String template, Integer status, String path, String expectedText) {
        var context = new Context();
        context.setVariables(Map.of());
        if (status != null) context.setVariable("status", status);
        if (path != null) context.setVariable("path", path);

        var html = engine.process(template, context);

        assertTrue(html.contains(expectedText), () -> template + " should contain \"" + expectedText + "\" but was:\n" + html);
        assertTrue(html.contains("Go to Resona AI"));
        assertFalse(html.contains("Whitelabel"));
    }

}
