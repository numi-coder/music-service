package kz.genvibe.media_management.service.internal.impl;

import kz.genvibe.media_management.client.supabase.SupabaseStorageClient;
import kz.genvibe.media_management.config.props.AppProps;
import kz.genvibe.media_management.config.props.IntegrationProps;
import kz.genvibe.media_management.service.internal.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final SupabaseStorageClient supabaseStorageClient;
    private final IntegrationProps integrationProps;
    private final AppProps appProps;

    @Override
    public String saveMp3(byte[] content) {
        var fileName = UUID.randomUUID() + ".mp3";

        if (supabaseStorageClient.isEnabled()) {
            var bucket = integrationProps.getSupabase().jingleBucketOrDefault();
            var url = supabaseStorageClient.upload(bucket, fileName, content, AUDIO_MPEG);
            log.info("Uploaded {} to Supabase bucket {}", fileName, bucket);
            return url;
        }

        return saveToLocalDisk(fileName, content);
    }

    @SneakyThrows
    private String saveToLocalDisk(String fileName, byte[] content) {
        var directory = Paths.get(appProps.getFileStorage().uploadDir());
        var filePath = directory.resolve(fileName);

        Files.createDirectories(directory);
        Files.write(filePath, content);

        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(appProps.getFileStorage().uploadDir())
            .path("/")
            .path(fileName)
            .toUriString();
    }

}
