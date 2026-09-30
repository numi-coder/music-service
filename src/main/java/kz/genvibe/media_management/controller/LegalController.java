package kz.genvibe.media_management.controller;

import kz.genvibe.media_management.config.I18n;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

/** Public Terms of Service and Privacy Policy pages. */
@Controller
public class LegalController {

    @GetMapping("/terms")
    public String terms(Locale locale) {
        return "pages/legal/terms" + suffix(locale);
    }

    @GetMapping("/privacy")
    public String privacy(Locale locale) {
        return "pages/legal/privacy" + suffix(locale);
    }

    private static String suffix(Locale locale) {
        return I18n.RUSSIAN.equals(I18n.supported(locale)) ? "_ru" : "";
    }

}
