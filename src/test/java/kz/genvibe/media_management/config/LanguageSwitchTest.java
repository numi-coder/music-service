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
        var localeResolver = new I18nConfig().localeResolver();
        mockMvc = MockMvcBuilders.standaloneSetup(new LegalController())
            .setLocaleResolver(localeResolver)
            .addInterceptors(I18nConfig.languageSwitch(localeResolver))
            .build();
    }

    @Test
    @DisplayName("First visit: Russian browsers get Russian, everyone else English")
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
