package kz.genvibe.media_management.config;

import kz.genvibe.media_management.model.domain.dto.user.PasswordSetupDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationMessagesTest {

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Form errors are shown in the person's language")
    void formErrorsAreTranslated() {
        assertEquals(Set.of("Password must be at least 6 characters long"), passwordErrors(Locale.ENGLISH));
        assertEquals(Set.of("Пароль должен быть не короче 6 символов"), passwordErrors(I18n.RUSSIAN));
    }

    /** Validates the way the app does: Spring Boot resolves {keys} from the app's texts. */
    private static Set<String> passwordErrors(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.setMessageInterpolator(new MessageInterpolatorFactory(I18n.messageSource()).getObject());
            validator.afterPropertiesSet();

            return validator.validate(new PasswordSetupDto(null, "abc1", "abc1")).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
        }
    }

}
