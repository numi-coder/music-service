package kz.genvibe.media_management.config;

import kz.genvibe.media_management.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nTest {

    @Test
    @DisplayName("Every English text has a Russian translation, and the other way round")
    void bothLanguagesHaveTheSameTexts() throws Exception {
        assertEquals(load("messages.properties").keySet(), load("messages_ru.properties").keySet());
    }

    @Test
    @DisplayName("Every choice shown to customers has a name in both languages")
    void everyChoiceIsTranslated() throws Exception {
        var english = load("messages.properties");
        List<Enum<?>[]> choices = List.of(
            BusinessType.values(), MusicProvider.values(), MusicAtmosphere.values(), CurrentFeel.values(),
            MusicMood.values(), PlaytimeWindow.values(), JingleCategory.values(), JingleRepeatingTime.values()
        );
        for (var values : choices) {
            for (var value : values) {
                var key = "enum." + value.getClass().getSimpleName() + "." + value.name();
                assertTrue(english.containsKey(key), key);
            }
        }
        for (var mood : MusicMood.values()) assertTrue(english.containsKey("enum.MusicMood." + mood.name() + ".desc"));
        for (var voice : JingleVoice.values()) assertTrue(english.containsKey("enum.JingleVoice." + voice.name() + ".desc"));
        for (var category : JingleCategory.values()) assertTrue(english.containsKey("js.category." + category.name()));
    }

    @Test
    @DisplayName("Only English and Russian are offered; anything else falls back to English")
    void unsupportedLanguagesFallBackToEnglish() {
        assertEquals(I18n.RUSSIAN, I18n.supported(Locale.forLanguageTag("ru-KZ")));
        assertEquals(Locale.ENGLISH, I18n.supported(Locale.forLanguageTag("zh-SG")));
        assertEquals(Locale.ENGLISH, I18n.supported(null));
    }

    @Test
    @DisplayName("Scripts get their texts in the viewer's language")
    void scriptTexts() {
        assertEquals("Link copied", I18n.scriptTexts(Locale.ENGLISH).get("link.copied"));
        assertEquals("Ссылка скопирована", I18n.scriptTexts(I18n.RUSSIAN).get("link.copied"));
    }

    @Test
    @DisplayName("The sign-in page renders in Russian with the language switch")
    void loginPageInRussian() {
        var html = render("pages/auth/login", I18n.RUSSIAN, Map.of("error", ""));

        assertTrue(html.contains("<html lang=\"ru\""), html);
        assertTrue(html.contains("<title>Вход</title>"), html);
        assertTrue(html.contains("Неверный email или пароль"));
        assertTrue(html.contains("placeholder=\"Пароль\""));
        assertTrue(html.contains("href=\"?lang=en\""));
        assertTrue(html.contains("<script src=\"/js/texts.js?lang=ru\"></script>"), html);
        assertFalse(html.contains("??"), "a text is missing from the message files");
    }

    @Test
    @DisplayName("The sign-up steps show the choices in Russian")
    void onboardingChoicesInRussian() {
        var html = render("pages/auth/onboarding/customer-feel", I18n.RUSSIAN, Map.of(), Map.of("spacePurposes", MusicMood.values()));

        assertTrue(html.contains("Замедлиться и расслабиться"), html);
        assertTrue(html.contains("Лучше всего подходит для: кофеен"));
        assertTrue(html.contains("value=\"SLOW_DOWN_AND_RELAX\""), "the submitted value stays the same in every language");
        assertFalse(html.contains("??"));
    }

    private static String render(String template, Locale locale, Map<String, String> params) {
        return render(template, locale, params, Map.of());
    }

    private static String render(String template, Locale locale, Map<String, String> params, Map<String, Object> model) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(I18n.messageSource());

        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        params.forEach(request::addParameter);
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(request, new MockHttpServletResponse());
        var context = new WebContext(exchange, locale);
        context.setVariables(model);
        return engine.process(template, context);
    }

    private static Properties load(String file) throws Exception {
        var properties = new Properties();
        try (var in = I18nTest.class.getClassLoader().getResourceAsStream(file)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

}
