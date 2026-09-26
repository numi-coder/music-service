package kz.genvibe.media_management.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kz.genvibe.media_management.config.LandingPage;
import kz.genvibe.media_management.config.annotations.CurrentUser;
import kz.genvibe.media_management.controller.auth.PasswordSetupSession;
import kz.genvibe.media_management.model.domain.dto.user.AppUserUpdateDto;
import kz.genvibe.media_management.model.domain.dto.user.PasswordSetupDto;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.service.internal.AuthService;
import kz.genvibe.media_management.service.internal.PasswordResetService;
import kz.genvibe.media_management.service.internal.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final LandingPage landingPage;

    /**
     * Sets the password of the user who just opened a verification or reset link
     * in this session. The email in the form is ignored: it must never decide
     * whose password is changed.
     */
    @PostMapping("/finalize")
    public String setupUserPassword(
        @Valid @ModelAttribute PasswordSetupDto passwordSetupDto,
        BindingResult bindingResult,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        var session = request.getSession(false);
        var userId = PasswordSetupSession.userId(session);
        if (userId.isEmpty()) return "redirect:/auth/login?setupExpired";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/auth/register";
        }
        if (!passwordSetupDto.passwordMatches()) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match");
            return "redirect:/auth/register";
        }

        var appUser = userService.setPassword(userId.get(), passwordSetupDto.password());
        passwordResetService.deleteTokens(appUser);
        PasswordSetupSession.clear(session);

        authService.authenticate(appUser, request, response);
        return "redirect:" + landingPage.pathFor(appUser.getEmail(), appUser.getRole());
    }

    @PatchMapping
    public String updateUser(
        @ModelAttribute AppUserUpdateDto dto,
        @CurrentUser AppUser appUser,
        HttpSession session
    ) {
        var isEmailChanged = !dto.email().equals(appUser.getEmail());
        userService.updateUser(dto, appUser);
        if (isEmailChanged) session.invalidate();

        return isEmailChanged ? "redirect:/auth/confirm?email=" + appUser.getEmail() : "redirect:/settings";
    }

}
