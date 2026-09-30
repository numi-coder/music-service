package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.I18n;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The texts page scripts need, as a script: t('link.copied') returns the text in
 * the viewer's language. Served as its own file so pages stay small.
 */
@Controller
public class ScriptTextsController {

    private static final MediaType JAVASCRIPT = new MediaType("text", "javascript", StandardCharsets.UTF_8);

    @GetMapping("/js/texts.js")
    public ResponseEntity<String> texts(@RequestParam(name = "lang", required = false) String lang) {
        var locale = I18n.supported(lang == null ? null : Locale.forLanguageTag(lang));
        var texts = I18n.scriptTexts(locale).entrySet().stream()
            .map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
            .collect(Collectors.joining(",", "{", "}"));

        var script = """
            window.RESONA_TEXT = %s;
            window.RESONA_LOCALE = %s;
            function t(key) {
                var text = window.RESONA_TEXT[key] || key;
                for (var i = 1; i < arguments.length; i++) {
                    text = text.split('{' + (i - 1) + '}').join(arguments[i]);
                }
                return text;
            }
            """.formatted(texts, quote(I18n.RUSSIAN.equals(locale) ? "ru-RU" : "en-GB"));

        return ResponseEntity.ok()
            .contentType(JAVASCRIPT)
            .cacheControl(CacheControl.noCache())
            .body(script);
    }

    /** A JavaScript string literal. */
    private static String quote(String value) {
        var backslash = (char) 92;
        var quoted = new StringBuilder().append('"');
        for (var c : value.toCharArray()) {
            if (c == '"' || c == backslash) {
                quoted.append(backslash).append(c);
            } else if (c < ' ' || c == '<') {
                quoted.append(backslash).append('x').append(String.format("%02X", (int) c));
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

}
