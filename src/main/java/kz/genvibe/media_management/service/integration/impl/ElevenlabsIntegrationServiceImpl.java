package kz.genvibe.media_management.service.integration.impl;

import kz.genvibe.media_management.client.base.OutgoingRequestService;
import kz.genvibe.media_management.client.dto.request.ElevenlabsTtsRequest;
import kz.genvibe.media_management.client.elevenlabs.ElevenlabsClient;
import kz.genvibe.media_management.service.integration.ElevenlabsIntegrationService;
import kz.genvibe.media_management.service.internal.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElevenlabsIntegrationServiceImpl implements ElevenlabsIntegrationService {

    private final OutgoingRequestService outgoingRequestService;
    private final ElevenlabsClient elevenlabsClient;
    private final FileStorageService fileStorageService;

    public String getSpeechFileUrl(String text, String voiceId) {
        var requestBody = new ElevenlabsTtsRequest(
            text,
            "eleven_v3"
        );

        var fileBytes = elevenlabsClient.textToSpeech(voiceId, requestBody);

        return fileStorageService.saveMp3(fileBytes);
    }

}
