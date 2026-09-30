package kz.genvibe.media_management.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptTextsControllerTest {

    private final ScriptTextsController controller = new ScriptTextsController();

    @Test
    @DisplayName("Scripts get Russian texts for ?lang=ru")
    void russian() {
        var response = controller.texts("ru");

        assertEquals("text/javascript;charset=UTF-8", String.valueOf(response.getHeaders().getContentType()));
        assertTrue(response.getBody().contains("\"link.copied\":\"Ссылка скопирована\""), response.getBody());
        assertTrue(response.getBody().contains("window.RESONA_LOCALE = \"ru-RU\";"));
        assertTrue(response.getBody().contains("function t(key)"));
    }

    @Test
    @DisplayName("Anything else gets English")
    void englishByDefault() {
        assertTrue(controller.texts(null).getBody().contains("\"link.copied\":\"Link copied\""));
        assertTrue(controller.texts("xx").getBody().contains("window.RESONA_LOCALE = \"en-GB\";"));
    }

}
