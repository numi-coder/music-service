package kz.genvibe.media_management.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings for AI music generation (prefix "music-generation"). Everything has a
 * safe default: nothing is generated, and nothing is spent, until "enabled" is
 * switched on.
 */
@Component
@ConfigurationProperties(prefix = "music-generation")
@Data
public class MusicGenerationProps {

    /** Master switch for the background worker that spends money. */
    private boolean enabled = false;

    /** Emails of the people allowed to open the music catalog page. */
    private List<String> operatorEmails = new ArrayList<>();

    /** Model name sent to ElevenLabs. */
    private String elevenlabsModel = "music_v1";

    /** Length of each generated track. */
    private int trackSeconds = 180;

    /** What the provider charges per generated minute. */
    private BigDecimal pricePerMinuteUsd = new BigDecimal("0.15");

    /** Generation stops for the month once this much has been spent. */
    private BigDecimal monthlyBudgetUsd = new BigDecimal("10");

    /** How many times a track is tried before it is marked as failed. */
    private int maxAttempts = 3;

    /** Supabase bucket for generated tracks, when Supabase storage is configured. */
    private String bucket = "music";

    /** The ffmpeg program used for loudness and silence checks; checks are skipped if it isn't installed. */
    private String ffmpegPath = "ffmpeg";

    public boolean isOperator(String email) {
        return email != null && operatorEmails.stream().anyMatch(email::equalsIgnoreCase);
    }

}
