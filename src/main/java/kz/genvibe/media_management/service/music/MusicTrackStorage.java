package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.client.supabase.SupabaseStorageClient;
import kz.genvibe.media_management.config.props.AppProps;
import kz.genvibe.media_management.config.props.MusicGenerationProps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Keeps generated tracks where store players can stream them: Supabase Storage
 * when it is configured, otherwise the server's own files folder.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MusicTrackStorage {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final SupabaseStorageClient supabaseStorageClient;
    private final MusicGenerationProps props;
    private final AppProps appProps;

    @SneakyThrows
    public String save(byte[] mp3) {
        var fileName = "track-" + UUID.randomUUID() + ".mp3";

        if (supabaseStorageClient.isEnabled()) {
            return supabaseStorageClient.upload(props.getBucket(), fileName, mp3, AUDIO_MPEG);
        }

        var directory = Paths.get(appProps.getFileStorage().uploadDir());
        Files.createDirectories(directory);
        Files.write(directory.resolve(fileName), mp3);
        return stripTrailingSlash(appProps.getBaseUrl()) + "/files/" + fileName;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
