package kz.genvibe.media_management.controller.music;

import kz.genvibe.media_management.config.props.MusicGenerationProps;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import kz.genvibe.media_management.model.enums.MusicMood;
import kz.genvibe.media_management.repository.MusicGenerationJobRepository;
import kz.genvibe.media_management.service.music.MusicGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * The Resona team's own page for building the AI music catalog: queue tracks,
 * listen, approve or reject. Business owners can't open it; only the emails in
 * music-generation.operator-emails can.
 */
@Controller
@RequestMapping("/operator/music")
@RequiredArgsConstructor
public class MusicCatalogController {

    private static final String PAGE = "redirect:/operator/music";

    private final MusicGenerationService musicGenerationService;
    private final MusicGenerationJobRepository jobRepository;
    private final MusicGenerationProps props;

    @GetMapping
    public String catalog(Authentication authentication, Model model) {
        requireOperator(authentication);

        model.addAttribute("jobs", musicGenerationService.recentJobs());
        model.addAttribute("atmospheres", MusicAtmosphere.values());
        model.addAttribute("moods", MusicMood.values());
        model.addAttribute("enabled", props.isEnabled());
        model.addAttribute("spent", musicGenerationService.spentThisMonth());
        model.addAttribute("budget", props.getMonthlyBudgetUsd());
        model.addAttribute("costPerTrack", musicGenerationService.costPerTrack());
        model.addAttribute("trackSeconds", props.getTrackSeconds());
        model.addAttribute("queued", jobRepository.countByStatus(MusicJobStatus.QUEUED));
        model.addAttribute("inReview", jobRepository.countByStatus(MusicJobStatus.IN_REVIEW));
        model.addAttribute("approved", jobRepository.countByStatus(MusicJobStatus.APPROVED));
        return "pages/operator/music-catalog";
    }

    @PostMapping("/queue")
    public String queue(
        @RequestParam MusicAtmosphere atmosphere,
        @RequestParam List<MusicMood> moods,
        @RequestParam int count,
        Authentication authentication,
        RedirectAttributes redirectAttributes
    ) {
        requireOperator(authentication);
        try {
            var jobs = musicGenerationService.queue(atmosphere, moods, count, authentication.getName());
            redirectAttributes.addFlashAttribute("notice", jobs.size() + " track(s) added to the queue.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("problem", e.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        return act(authentication, redirectAttributes, () -> musicGenerationService.approve(id), "Track added to the music library.");
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        return act(authentication, redirectAttributes, () -> musicGenerationService.reject(id), "Track rejected.");
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        return act(authentication, redirectAttributes, () -> musicGenerationService.retry(id), "Track put back in the queue.");
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        return act(authentication, redirectAttributes, () -> musicGenerationService.cancel(id), "Track removed from the queue.");
    }

    private String act(Authentication authentication, RedirectAttributes redirectAttributes, Runnable action, String notice) {
        requireOperator(authentication);
        try {
            action.run();
            redirectAttributes.addFlashAttribute("notice", notice);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("problem", e.getMessage());
        }
        return PAGE;
    }

    /** Anyone else gets "page not found", so the page's existence isn't advertised. */
    private void requireOperator(Authentication authentication) {
        if (authentication == null || !props.isOperator(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

}
