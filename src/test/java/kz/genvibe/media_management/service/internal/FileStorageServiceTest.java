package kz.genvibe.media_management.service.internal;

import kz.genvibe.media_management.client.supabase.SupabaseStorageClient;
import kz.genvibe.media_management.config.props.AppProps;
import kz.genvibe.media_management.config.props.IntegrationProps;
import kz.genvibe.media_management.service.internal.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private SupabaseStorageClient supabaseStorageClient;

    @Mock
    private IntegrationProps integrationProps;

    @Mock
    private AppProps appProps;

    @InjectMocks
    private FileStorageServiceImpl fileStorageService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Uploads to the Supabase jingles bucket when Supabase is configured")
    void saveMp3_uploadsToSupabase() {
        final var content = "fake mp3 content".getBytes();
        final var publicUrl = "https://example.supabase.co/storage/v1/object/public/jingles/abc.mp3";

        when(supabaseStorageClient.isEnabled()).thenReturn(true);
        when(integrationProps.getSupabase())
            .thenReturn(new IntegrationProps.SupabaseProperties("https://example.supabase.co", "secret", null));
        when(supabaseStorageClient.upload(eq("jingles"), anyString(), eq(content), any(MediaType.class)))
            .thenReturn(publicUrl);

        final var result = fileStorageService.saveMp3(content);

        assertEquals(publicUrl, result);
        verify(supabaseStorageClient).upload(eq("jingles"), anyString(), eq(content), any(MediaType.class));
    }

    @Test
    @DisplayName("Falls back to local disk when Supabase is not configured")
    void saveMp3_savesToLocalDisk() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        final var uploadDir = "build/test-uploads";

        when(supabaseStorageClient.isEnabled()).thenReturn(false);
        when(appProps.getFileStorage()).thenReturn(new AppProps.FileStorageProperties(uploadDir));

        final var result = fileStorageService.saveMp3("fake mp3 content".getBytes());

        assertTrue(result.contains("/files/"));
        assertTrue(result.endsWith(".mp3"));

        final var fileName = result.substring(result.lastIndexOf("/") + 1);
        final var expectedPath = Paths.get(uploadDir).resolve(fileName);
        assertTrue(Files.exists(expectedPath), "File should be written to disk");

        Files.deleteIfExists(expectedPath);
    }

}
