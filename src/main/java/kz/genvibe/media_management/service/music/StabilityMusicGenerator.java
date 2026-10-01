package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.config.props.MusicGenerationProps;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Stable Audio by Stability AI: https://platform.stability.ai/docs/api-reference (text-to-audio).
 * Under Stability's terms the generated tracks belong to us, so they can be played
 * in our customers' venues. A flat price per track, up to 190 seconds long.
 */
@Component
public class StabilityMusicGenerator implements MusicGenerator {

    static final String NAME = "stability";
    static final int MAX_SECONDS = 190;
    private static final String BASE_URL = "https://api.stability.ai/v2beta/audio/stable-audio-2";

    private final RestClient restClient;
    private final MusicGenerationProps props;

    public StabilityMusicGenerator(MusicGenerationProps props, RestClient.Builder builder) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMinutes(5));
        this.props = props;
        this.restClient = builder.baseUrl(BASE_URL).requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public byte[] generate(String prompt, int lengthSeconds) {
        var key = props.getStabilityApiKey();
        if (key == null || key.isBlank()) {
            throw new MusicGenerationException("No Stability AI key is set (music-generation.stability-api-key)", false, null);
        }

        var form = new LinkedMultiValueMap<String, Object>();
        form.add("prompt", prompt);
        form.add("duration", String.valueOf(Math.min(lengthSeconds, MAX_SECONDS)));
        form.add("model", props.getStabilityModel());
        form.add("output_format", "mp3");
        // Stability expects multipart even without files.
        form.add("none", new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "none";
            }
        });

        try {
            var audio = restClient.post()
                .uri("/text-to-audio")
                .header("authorization", "Bearer " + key)
                .header("stability-client-id", "resona-ai")
                .accept(MediaType.parseMediaType("audio/*"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new MusicGenerationException("Stability AI returned an empty track", true, null);
            }
            return audio;
        } catch (RestClientResponseException e) {
            var status = e.getStatusCode().value();
            var worthRetrying = status == 429 || status >= 500;
            var body = e.getResponseBodyAsString();
            throw new MusicGenerationException(
                "Stability AI answered %d: %s".formatted(status, body.length() > 500 ? body.substring(0, 500) + "…" : body),
                worthRetrying, e
            );
        } catch (RestClientException e) {
            throw new MusicGenerationException("Could not reach Stability AI: " + e.getMessage(), true, e);
        }
    }

}
