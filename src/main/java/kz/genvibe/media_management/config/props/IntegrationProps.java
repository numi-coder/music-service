package kz.genvibe.media_management.config.props;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "integration")
@Data
@Validated
public class IntegrationProps {

    @NotNull
    private ElevenlabsProperties elevenlabs;

    /**
     * Optional. When url and secret key are set, generated audio is uploaded to Supabase Storage.
     * When they are missing, files keep being saved to the local upload directory.
     */
    private SupabaseProperties supabase;

    public record ElevenlabsProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey
    ) {}

    public record SupabaseProperties(
        String url,
        String secretKey,
        String jingleBucket
    ) {
        private static final String DEFAULT_JINGLE_BUCKET = "jingles";

        public boolean hasCredentials() {
            return url != null && !url.isBlank() && secretKey != null && !secretKey.isBlank();
        }

        public String jingleBucketOrDefault() {
            return jingleBucket == null || jingleBucket.isBlank() ? DEFAULT_JINGLE_BUCKET : jingleBucket;
        }
    }

}
