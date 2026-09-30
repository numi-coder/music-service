package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.config.props.IntegrationProps;
import kz.genvibe.media_management.config.props.MusicGenerationProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/** Eleven Music: https://elevenlabs.io/docs/api-reference/music/compose */
@Component
@Slf4j
public class ElevenlabsMusicGenerator implements MusicGenerator {

    static final String NAME = "elevenlabs";
    private static final String OUTPUT_FORMAT = "mp3_44100_128";
    /** A few minutes of music can take a while to come back. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final MusicGenerationProps props;

    public ElevenlabsMusicGenerator(IntegrationProps integrationProps, MusicGenerationProps props, RestClient.Builder builder) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.props = props;
        this.restClient = builder
            .baseUrl(integrationProps.getElevenlabs().baseUrl())
            .defaultHeader("xi-api-key", integrationProps.getElevenlabs().apiKey())
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public byte[] generate(String prompt, int lengthSeconds) {
        try {
            var audio = restClient.post()
                .uri(uri -> uri.path("/music").queryParam("output_format", OUTPUT_FORMAT).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "prompt", prompt,
                    "music_length_ms", lengthSeconds * 1000,
                    "model_id", props.getElevenlabsModel(),
                    "force_instrumental", true
                ))
                .retrieve()
                .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new MusicGenerationException("ElevenLabs returned an empty track", true, null);
            }
            return audio;
        } catch (RestClientResponseException e) {
            // 4xx: the plan, the key or the prompt is the problem; 429 and 5xx pass with time.
            var status = e.getStatusCode().value();
            var worthRetrying = status == 429 || status >= 500;
            throw new MusicGenerationException(
                "ElevenLabs answered %d: %s".formatted(status, shorten(e.getResponseBodyAsString())), worthRetrying, e
            );
        } catch (RestClientException e) {
            throw new MusicGenerationException("Could not reach ElevenLabs: " + e.getMessage(), true, e);
        }
    }

    private static String shorten(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

}
