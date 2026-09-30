package kz.genvibe.media_management.model.domain.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordSetupDto(
    @Email String email,

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 6, message = "{validation.password.length}")
    @Pattern(regexp = ".*[0-9].*", message = "{validation.password.digit}")
    String password,

    @NotBlank(message = "{validation.password.confirm}")
    String confirmPassword
) {
    public boolean passwordMatches() {
        return password.equals(confirmPassword);
    }
}
