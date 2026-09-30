package kz.genvibe.media_management.config;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

/**
 * The platform's languages (English and Russian) and its texts, which live in
 * messages.properties and messages_ru.properties.
 */
public final class I18n {

    public static final Locale RUSSIAN = Locale.of("ru");

    private static final String BASENAME = "messages";
    private static final String SCRIPT_PREFIX = "js.";
    private static final ResourceBundleMessageSource MESSAGES = createMessageSource();

    private I18n() {
    }

    public static MessageSource messageSource() {
        return MESSAGES;
    }

    /** Russian for Russian speakers, English for everyone else. */
    public static Locale supported(Locale requested) {
        return requested != null && RUSSIAN.getLanguage().equals(requested.getLanguage()) ? RUSSIAN : Locale.ENGLISH;
    }

    /** The language of the person making the current request. */
    public static Locale current() {
        return supported(LocaleContextHolder.getLocale());
    }

    public static String text(String key, Object... args) {
        return MESSAGES.getMessage(key, args, current());
    }

    /** The texts used by scripts in the browser: the "js." keys, without the prefix. */
    public static Map<String, String> scriptTexts(Locale locale) {
        var language = supported(locale);
        var texts = new TreeMap<String, String>();
        for (var key : ResourceBundle.getBundle(BASENAME, Locale.ROOT).keySet()) {
            if (key.startsWith(SCRIPT_PREFIX)) {
                texts.put(key.substring(SCRIPT_PREFIX.length()), MESSAGES.getMessage(key, null, language));
            }
        }
        return texts;
    }

    private static ResourceBundleMessageSource createMessageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename(BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

}
