package kz.genvibe.media_management.service.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the automatic listen found in a track. Loudness and silence are only
 * known when ffmpeg measured them; otherwise they are null.
 *
 * @param durationSeconds       how long the track is
 * @param loudnessLufs          overall loudness (LUFS; streaming services sit around -14 to -16)
 * @param longestSilenceSeconds the longest stretch of silence inside the track
 * @param leadingSilenceSeconds silence before the music starts
 */
public record AudioReport(
    double durationSeconds,
    Double loudnessLufs,
    Double longestSilenceSeconds,
    Double leadingSilenceSeconds
) {

    /** Loudness every track is brought to, so stores don't jump in volume between tracks. */
    public static final double TARGET_LUFS = -16.0;
    static final double LOUDNESS_TOLERANCE = 1.5;
    static final double MAX_SILENCE_SECONDS = 3.0;
    static final double MAX_LEADING_SILENCE_SECONDS = 1.5;
    static final double MAX_LENGTH_DIFFERENCE = 0.2;
    /** The MP3 bitrate we ask providers for, used to estimate length without ffmpeg. */
    private static final int BITS_PER_SECOND = 128_000;

    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Pattern LOUDNESS = Pattern.compile("\"input_i\"\\s*:\\s*\"(-?\\d+(?:\\.\\d+)?|-inf)\"");
    private static final Pattern SILENCE_START = Pattern.compile("silence_start: (-?\\d+(?:\\.\\d+)?)");
    private static final Pattern SILENCE_DURATION = Pattern.compile("silence_duration: (\\d+(?:\\.\\d+)?)");

    public boolean measured() {
        return loudnessLufs != null;
    }

    public boolean needsLoudnessFix() {
        return measured() && Math.abs(loudnessLufs - TARGET_LUFS) > LOUDNESS_TOLERANCE;
    }

    /** Problems a reviewer should know about; empty when the track looks fine. */
    public List<String> problems(int requestedSeconds) {
        var problems = new ArrayList<String>();

        if (Math.abs(durationSeconds - requestedSeconds) > requestedSeconds * MAX_LENGTH_DIFFERENCE) {
            problems.add(String.format(Locale.ROOT, "Length is %.0f s, asked for %d s", durationSeconds, requestedSeconds));
        }
        if (!measured()) {
            problems.add("Loudness and silence were not checked (ffmpeg is not installed)");
            return problems;
        }
        if (loudnessLufs < -70) {
            problems.add("The track is silent");
        } else if (needsLoudnessFix()) {
            problems.add(String.format(Locale.ROOT, "Loudness is %.1f LUFS, target is %.0f", loudnessLufs, TARGET_LUFS));
        }
        if (leadingSilenceSeconds != null && leadingSilenceSeconds > MAX_LEADING_SILENCE_SECONDS) {
            problems.add(String.format(Locale.ROOT, "Starts with %.1f s of silence", leadingSilenceSeconds));
        }
        if (longestSilenceSeconds != null && longestSilenceSeconds > MAX_SILENCE_SECONDS) {
            problems.add(String.format(Locale.ROOT, "Contains %.1f s of silence", longestSilenceSeconds));
        }
        return problems;
    }

    /** When ffmpeg isn't available: the length follows from the file size at a fixed bitrate. */
    public static AudioReport estimatedFromSize(long bytes) {
        return new AudioReport(bytes * 8.0 / BITS_PER_SECOND, null, null, null);
    }

    /**
     * Reads what ffmpeg printed for
     * {@code -af silencedetect=noise=-50dB:d=1,loudnorm=print_format=json -f null -}.
     */
    public static AudioReport parseFfmpegOutput(String output) {
        var duration = DURATION.matcher(output);
        if (!duration.find()) throw new IllegalArgumentException("ffmpeg did not report a duration");
        var seconds = Integer.parseInt(duration.group(1)) * 3600
            + Integer.parseInt(duration.group(2)) * 60
            + Double.parseDouble(duration.group(3));

        var loudness = LOUDNESS.matcher(output);
        if (!loudness.find()) throw new IllegalArgumentException("ffmpeg did not report loudness");
        var lufs = "-inf".equals(loudness.group(1)) ? -99.0 : Double.parseDouble(loudness.group(1));

        var longest = 0.0;
        var leading = 0.0;
        var starts = SILENCE_START.matcher(output);
        var lengths = SILENCE_DURATION.matcher(output);
        while (starts.find()) {
            var start = Double.parseDouble(starts.group(1));
            // A silence that runs to the very end has no reported length.
            var length = lengths.find() ? Double.parseDouble(lengths.group(1)) : seconds - start;
            if (start <= 0.05) leading = length;
            // Fading out at the end is fine; silence anywhere else is not.
            var isEnding = start + length >= seconds - 0.5;
            if (!isEnding || length > 2 * MAX_SILENCE_SECONDS) longest = Math.max(longest, length);
        }

        return new AudioReport(seconds, lufs, longest, leading);
    }

}
