package kz.genvibe.media_management.controller.jingle;

import jakarta.validation.Valid;
import kz.genvibe.media_management.config.annotations.CurrentUser;
import kz.genvibe.media_management.exception.JingleCreationLimitExceededException;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleApproveDto;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleCreateDto;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleScheduleUpdateDto;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.enums.JingleVoice;
import kz.genvibe.media_management.service.integration.VoicePreviewService;
import kz.genvibe.media_management.service.internal.JingleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.List;

@Controller
@RequestMapping("/api/jingles")
@RequiredArgsConstructor
public class JingleActionController {

    private final JingleService jingleService;
    private final VoicePreviewService voicePreviewService;

    /** Redirects to ElevenLabs' sample clip for the voice; costs no generation credit. */
    @GetMapping("/voice-preview/{voice}")
    public ResponseEntity<Void> voicePreview(@PathVariable JingleVoice voice) {
        return voicePreviewService.getPreviewUrl(voice)
            .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).<Void>build())
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/schedule")
    @ResponseBody
    public void updateSchedule(
        @PathVariable Long id,
        @Valid @RequestBody JingleScheduleUpdateDto dto,
        @CurrentUser AppUser appUser
    ) {
        jingleService.updateJingleSchedule(id, dto, appUser);
    }

    @PostMapping
    public String createJingle(
        @CurrentUser AppUser appUser,
        @Valid JingleCreateDto dto,
        RedirectAttributes redirectAttributes
    ) {
        try {
            jingleService.createJingle(appUser, dto);
        } catch (JingleCreationLimitExceededException e) {
            redirectAttributes.addFlashAttribute("jingleError", e.getMessage());
        }
        return "redirect:/jingles";
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public void deleteJingle(
        @PathVariable Long id,
        @CurrentUser AppUser appUser
    ) {
        jingleService.deleteJingleById(id, appUser);
    }

    @PatchMapping("/{id}/stores")
    @ResponseBody
    public void addJingleToLocations(
        @PathVariable long id,
        @RequestBody List<Long> idList,
        @CurrentUser AppUser appUser
    ) {
        jingleService.addJingleToStores(id, idList, appUser);
    }

    @ResponseBody
    @PatchMapping("/approve-pause-request/{id}")
    public String approveJingle(
        @PathVariable long id,
        @CurrentUser AppUser appUser
    ) {
        jingleService.setPauseApprovalStatus(id, appUser);
        return "redirect:/jingles";
    }

    @ResponseBody
    @PatchMapping("/pause-request/{id}")
    public void requestToPause(@PathVariable long id) {
        jingleService.requestToPauseJingle(id);
    }

}
