package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.config.props.MusicGenerationProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Listens to a generated track with ffmpeg: measures its length, loudness and
 * silences, and evens out the loudness. Without ffmpeg on the machine the track
 * is passed through unchanged and the report says the checks were skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AudioInspector {

    private static final int TIMEOUT_MINUTES = 5;

    private final MusicGenerationProps props;

    public record Result(byte[] audio, AudioReport report, boolean loudnessFixed) {}

    public Result inspect(byte[] mp3) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("resona-track-", ".mp3");
            Files.write(input, mp3);

            var report = measure(input);
            if (!report.needsLoudnessFix() || report.loudnessLufs() < -70) {
                return new Result(mp3, report, false);
            }

            output = Files.createTempFile("resona-track-fixed-", ".mp3");
            run(List.of(
                props.getFfmpegPath(), "-hide_banner", "-nostats", "-y", "-i", input.toString(),
                "-af", String.format(Locale.ROOT, "loudnorm=I=%.0f:TP=-1.5:LRA=11", AudioReport.TARGET_LUFS),
                "-ar", "44100", "-codec:a", "libmp3lame", "-b:a", "128k", output.toString()
            ));
            return new Result(Files.readAllBytes(output), measure(output), true);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Audio checks skipped: {}", e.getMessage());
            return new Result(mp3, AudioReport.estimatedFromSize(mp3.length), false);
        } finally {
            delete(input);
            delete(output);
        }
    }

    private AudioReport measure(Path file) throws IOException {
        return AudioReport.parseFfmpegOutput(run(List.of(
            props.getFfmpegPath(), "-hide_banner", "-nostats", "-i", file.toString(),
            "-af", "silencedetect=noise=-50dB:d=1,loudnorm=print_format=json", "-f", "null", "-"
        )));
    }

    /** Runs ffmpeg and returns everything it printed. Throws IOException when ffmpeg is missing or fails. */
    private static String run(List<String> command) throws IOException {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var out = process.getInputStream()) {
            var printed = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("ffmpeg took too long");
            }
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg failed: " + printed.lines().reduce((first, last) -> last).orElse(""));
            }
            return printed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for ffmpeg", e);
        }
    }

    private static void delete(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary file {}", file);
        }
    }

}
