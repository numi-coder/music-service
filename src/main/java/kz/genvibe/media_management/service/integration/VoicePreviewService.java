package kz.genvibe.media_management.service.integration;

import kz.genvibe.media_management.client.elevenlabs.ElevenlabsClient;
import kz.genvibe.media_management.model.enums.JingleVoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sample clips ElevenLabs publishes for each voice, so users can hear a voice
 * before generating a jingle. Fetching them costs no generation credit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoicePreviewService {

    private final ElevenlabsClient elevenlabsClient;
    private final Map<JingleVoice, String> previewUrls = new ConcurrentHashMap<>();

    public Optional<String> getPreviewUrl(JingleVoice voice) {
        var cached = previewUrls.get(voice);
        if (cached != null) return Optional.of(cached);

        try {
            var previewUrl = elevenlabsClient.getVoice(voice.getElevenlabsVoiceId()).get("preview_url");
            if (previewUrl instanceof String url && url.startsWith("https://")) {
                previewUrls.put(voice, url);
                return Optional.of(url);
            }
        } catch (RuntimeException e) {
            log.warn("Could not load the ElevenLabs preview for voice {}: {}", voice, e.getMessage());
        }
        return Optional.empty();
    }

}
