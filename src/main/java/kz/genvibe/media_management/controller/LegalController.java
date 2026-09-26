package kz.genvibe.media_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public Terms of Service and Privacy Policy pages. */
@Controller
public class LegalController {

    @GetMapping("/terms")
    public String terms() {
        return "pages/legal/terms";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "pages/legal/privacy";
    }

}
