package kz.genvibe.media_management.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import kz.genvibe.media_management.service.internal.AuthService;
import kz.genvibe.media_management.service.internal.PasswordResetService;
import kz.genvibe.media_management.service.internal.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    /** Set-password page; only reachable right after opening a verification or reset link. */
    @GetMapping("/register")
    public String register(HttpSession session, Model model) {
        var userId = PasswordSetupSession.userId(session);
        if (userId.isEmpty()) return "redirect:/auth/login?setupExpired";

        model.addAttribute("email", userService.getUserById(userId.get()).getEmail());
        model.addAttribute("isReset", PasswordSetupSession.isReset(session));
        return "pages/auth/register";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "pages/auth/login";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, HttpSession session) {
        var appUser = authService.verifyEmail(token);
        PasswordSetupSession.allow(session, appUser, false);
        return "redirect:/auth/register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "pages/auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email) {
        passwordResetService.requestReset(email);
        return "redirect:/auth/forgot-password?sent";
    }

    /** The token stays valid until the password is actually changed, so link scanners can't use it up. */
    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token, HttpSession session) {
        return passwordResetService.findUserByValidToken(token)
            .map(appUser -> {
                PasswordSetupSession.allow(session, appUser, true);
                return "redirect:/auth/register";
            })
            .orElse("redirect:/auth/forgot-password?expired");
    }

    @GetMapping("/confirm")
    public String confirmEmail(
        @RequestParam @Email String email,
        Model model
    ) {
        model.addAttribute("email", email);
        authService.sendEmailVerification(email);
        return "pages/auth/confirm";
    }

    @GetMapping("/confirm-email")
    public String confirmPage(
        @RequestParam String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var appUser = authService.verifyEmail(token);
        authService.authenticate(appUser, request, response);
        return "pages/auth/confirm-email";
    }

    @PostMapping("/send-email")
    public String sendEmail(@RequestParam String email) {
        authService.sendEmailVerification(email);
        var appUser = userService.getUserByEmail(email);

        return appUser.isEmailChanged()
            ? "redirect:/auth/confirm-email" : "redirect:/onboarding/verification-sent";
    }

}
