package kz.genvibe.media_management.config;

import jakarta.servlet.http.Cookie;
import kz.genvibe.media_management.controller.LegalController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class LanguageSwitchTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var localeResolver = new I18nConfig(null).localeResolver();
        mockMvc = MockMvcBuilders.standaloneSetup(new LegalController())
            .setLocaleResolver(localeResolver)
            .addInterceptors(I18nConfig.languageSwitch(localeResolver))
            .build();
    }

    @Test
    @DisplayName("First visit: Russian in the post-Soviet countries, English everywhere else")
    void firstVisitFollowsTheCountry() throws Exception {
        // Kazakhstan, Russia, Uzbekistan, Latvia - whatever the browser's language is
        for (var address : new String[]{"2.72.0.1", "77.88.8.8", "84.54.64.1", "159.148.0.1"}) {
            mockMvc.perform(get("/terms").header("X-Real-IP", address).header("Accept-Language", "en-US,en"))
                .andExpect(view().name("pages/legal/terms_ru"));
        }
        // United States, Singapore - even with a Russian browser
        for (var address : new String[]{"8.8.8.8", "139.180.184.254"}) {
            mockMvc.perform(get("/terms").header("X-Real-IP", address).header("Accept-Language", "ru-RU,ru"))
                .andExpect(view().name("pages/legal/terms"));
        }
    }

    @Test
    @DisplayName("The visitor's own choice beats the country")
    void choiceBeatsCountry() throws Exception {
        mockMvc.perform(get("/terms").header("X-Real-IP", "2.72.0.1").cookie(new Cookie(I18nConfig.LANGUAGE_COOKIE, "en")))
            .andExpect(view().name("pages/legal/terms"));
        mockMvc.perform(get("/terms").header("X-Real-IP", "8.8.8.8").param("lang", "ru"))
            .andExpect(view().name("pages/legal/terms_ru"));
    }

    @Test
    @DisplayName("Unknown country: Russian browsers get Russian, everyone else English")
    void firstVisitFollowsTheBrowser() throws Exception {
        mockMvc.perform(get("/terms").header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8"))
            .andExpect(view().name("pages/legal/terms_ru"));
        mockMvc.perform(get("/terms").header("Accept-Language", "zh-SG,zh;q=0.9"))
            .andExpect(view().name("pages/legal/terms"));
        mockMvc.perform(get("/privacy"))
            .andExpect(view().name("pages/legal/privacy"));
    }

    @Test
    @DisplayName("The switch changes the language at once and remembers it")
    void switchIsRemembered() throws Exception {
        mockMvc.perform(get("/terms").param("lang", "ru").header("Accept-Language", "en-SG"))
            .andExpect(view().name("pages/legal/terms_ru"))
            .andExpect(cookie().value(I18nConfig.LANGUAGE_COOKIE, "ru"));

        mockMvc.perform(get("/privacy").cookie(new Cookie(I18nConfig.LANGUAGE_COOKIE, "ru")).header("Accept-Language", "en-SG"))
            .andExpect(view().name("pages/legal/privacy_ru"));

        mockMvc.perform(get("/terms").param("lang", "en").cookie(new Cookie(I18nConfig.LANGUAGE_COOKIE, "ru")))
            .andExpect(view().name("pages/legal/terms"))
            .andExpect(cookie().value(I18nConfig.LANGUAGE_COOKIE, "en"));
    }

    @Test
    @DisplayName("An unknown language falls back to English")
    void unknownLanguage() throws Exception {
        mockMvc.perform(get("/terms").param("lang", "xx"))
            .andExpect(view().name("pages/legal/terms"));
        mockMvc.perform(get("/terms").cookie(new Cookie(I18nConfig.LANGUAGE_COOKIE, "fr")))
            .andExpect(view().name("pages/legal/terms"));
    }

}
