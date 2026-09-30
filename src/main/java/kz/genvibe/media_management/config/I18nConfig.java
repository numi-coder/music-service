package kz.genvibe.media_management.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;

/**
 * Language choice. On the first visit it follows the visitor's country: Russian in
 * the post-Soviet countries, English everywhere else. After that it is whatever the
 * person picks with the EN / RU switch (any page link with ?lang=en or ?lang=ru),
 * remembered in a cookie.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    static final String LANGUAGE_COOKIE = "resona_lang";
    static final String LANGUAGE_PARAMETER = "lang";
    /** Set by nginx to the visitor's own address. */
    static final String VISITOR_ADDRESS_HEADER = "X-Real-IP";

    private final RussianFirstCountries russianFirstCountries = RussianFirstCountries.load();

    @Bean
    public MessageSource messageSource() {
        return I18n.messageSource();
    }

    @Bean
    public LocaleResolver localeResolver() {
        var resolver = new CookieLocaleResolver(LANGUAGE_COOKIE) {
            @Override
            protected Locale parseLocaleValue(@NonNull String localeValue) {
                return I18n.supported(super.parseLocaleValue(localeValue));
            }
        };
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setDefaultLocaleFunction(this::firstVisitLanguage);
        return resolver;
    }

    /** When the country can't be told from the address, the browser's language decides. */
    private Locale firstVisitLanguage(HttpServletRequest request) {
        var address = request.getHeader(VISITOR_ADDRESS_HEADER);
        return russianFirstCountries.contains(address != null ? address : request.getRemoteAddr())
            .map(russianFirst -> russianFirst ? I18n.RUSSIAN : Locale.ENGLISH)
            .orElseGet(() -> I18n.supported(request.getLocale()));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(languageSwitch(localeResolver()));
    }

    /** Switches the language when a page is opened with ?lang=en or ?lang=ru. */
    static HandlerInterceptor languageSwitch(LocaleResolver localeResolver) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler
            ) {
                var language = request.getParameter(LANGUAGE_PARAMETER);
                if (language != null && !language.isBlank()) {
                    localeResolver.setLocale(request, response, I18n.supported(Locale.forLanguageTag(language)));
                }
                return true;
            }
        };
    }

}
