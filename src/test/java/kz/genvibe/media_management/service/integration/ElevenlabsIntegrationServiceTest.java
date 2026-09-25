package kz.genvibe.media_management.service.integration;

import kz.genvibe.media_management.client.dto.request.ElevenlabsTtsRequest;
import kz.genvibe.media_management.client.elevenlabs.ElevenlabsClient;
import kz.genvibe.media_management.service.integration.impl.ElevenlabsIntegrationServiceImpl;
import kz.genvibe.media_management.service.internal.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ElevenlabsIntegrationServiceTest {

    @Mock
    private ElevenlabsClient client;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ElevenlabsIntegrationServiceImpl elevenlabsIntegrationService;

    @Test
    @DisplayName("Успешный кейс - генерация озвучки и сохранение файла")
    void getSpeechFileUrl_Success() {
        final var text = "test text";
        final var voiceId = "voice_123";
        final var mockBytes = "fake mp3 content".getBytes();
        final var storedUrl = "https://example.supabase.co/storage/v1/object/public/jingles/abc.mp3";

        when(client.textToSpeech(eq(voiceId), any(ElevenlabsTtsRequest.class))).thenReturn(mockBytes);
        when(fileStorageService.saveMp3(mockBytes)).thenReturn(storedUrl);

        final var resultUrl = elevenlabsIntegrationService.getSpeechFileUrl(text, voiceId);

        assertEquals(storedUrl, resultUrl);
        verify(fileStorageService).saveMp3(mockBytes);
    }

}
