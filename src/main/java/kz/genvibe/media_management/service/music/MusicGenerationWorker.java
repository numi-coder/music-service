package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.config.props.MusicGenerationProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Works through the music queue in the background, one track at a time, so
 * generation never slows the site down. Does nothing unless
 * music-generation.enabled=true.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MusicGenerationWorker {

    private final MusicGenerationService musicGenerationService;
    private final MusicGenerationProps props;

    @Scheduled(initialDelay = 60_000, fixedDelay = 30_000)
    public void generateNextTrack() {
        if (!props.isEnabled()) return;
        try {
            musicGenerationService.processNext();
        } catch (RuntimeException e) {
            log.error("Music generation worker failed", e);
        }
    }

}
