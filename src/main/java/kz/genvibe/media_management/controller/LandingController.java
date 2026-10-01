package kz.genvibe.media_management.controller;

import jakarta.servlet.http.HttpServletRequest;
import kz.genvibe.media_management.config.I18n;
import kz.genvibe.media_management.model.entity.LandingRequest;
import kz.genvibe.media_management.repository.LandingRequestRepository;
import kz.genvibe.media_management.service.internal.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** The public landing page and its "Leave us a request" form. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LandingController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final LandingRequestRepository landingRequestRepository;
    private final MailService mailService;

    /** Where new requests are emailed. */
    @Value("${landing.notify-email:numi@weresona.com}")
    private String notifyEmail;

    /** Recent requests per visitor address, so the form can't be used to flood the inbox. */
    private final Map<String, Deque<Instant>> recentByAddress = new ConcurrentHashMap<>();

    @GetMapping("/landing")
    public String landing() {
        return "pages/landing";
    }

    public record RequestForm(String companyName, String email, String website) {}

    @PostMapping("/landing/request")
    public ResponseEntity<Map<String, String>> request(@RequestBody RequestForm form, HttpServletRequest request) {
        // The hidden "website" field is only ever filled in by bots: pretend it worked.
        if (form.website() != null && !form.website().isBlank()) return ResponseEntity.ok(Map.of("status", "ok"));

        var company = form.companyName() == null ? "" : form.companyName().strip();
        var email = form.email() == null ? "" : form.email().strip();
        if (company.isEmpty() || company.length() > 160 || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("status", "invalid"));
        }
        if (tooManyFrom(visitorAddress(request))) {
            return ResponseEntity.status(429).body(Map.of("status", "slow-down"));
        }

        var language = I18n.current().getLanguage();
        landingRequestRepository.save(new LandingRequest(company, email, language));
        log.info("Landing page request saved for company '{}'", company);

        try {
            mailService.sendHtmlMail(notifyEmail, I18n.text("landing.email.subject", company), """
                <p><strong>New request from the Resona AI website</strong></p>
                <p>Company: %s<br>Email: <a href="mailto:%s">%s</a><br>Language: %s</p>
                """.formatted(HtmlUtils.htmlEscape(company), HtmlUtils.htmlEscape(email), HtmlUtils.htmlEscape(email), language));
        } catch (RuntimeException e) {
            // The request is saved either way; a mail hiccup must not lose it or show the visitor an error.
            log.warn("Could not email the landing page request: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean tooManyFrom(String address) {
        var now = Instant.now();
        var times = recentByAddress.computeIfAbsent(address, a -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) times.pollFirst();
            if (times.size() >= MAX_REQUESTS_PER_HOUR) return true;
            times.addLast(now);
        }
        if (recentByAddress.size() > 10_000) recentByAddress.clear();
        return false;
    }

    private static String visitorAddress(HttpServletRequest request) {
        var address = request.getHeader("X-Real-IP");
        return address != null ? address : request.getRemoteAddr();
    }

}
