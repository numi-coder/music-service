package kz.genvibe.media_management.service.internal.impl;

import kz.genvibe.media_management.exception.JingleCreationLimitExceededException;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleCreateDto;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleScheduleUpdateDto;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.entity.Jingle;
import kz.genvibe.media_management.model.entity.JingleGeneration;
import kz.genvibe.media_management.model.entity.Organization;
import kz.genvibe.media_management.model.enums.JingleCategory;
import kz.genvibe.media_management.model.enums.JingleRepeatingTime;
import kz.genvibe.media_management.model.enums.JingleSlotStatus;
import kz.genvibe.media_management.model.enums.JingleVoice;
import kz.genvibe.media_management.repository.JingleGenerationRepository;
import kz.genvibe.media_management.repository.JingleRepository;
import kz.genvibe.media_management.repository.JingleSlotRepository;
import kz.genvibe.media_management.service.integration.ElevenlabsIntegrationService;
import kz.genvibe.media_management.service.internal.JingleService;
import kz.genvibe.media_management.service.internal.StoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JingleServiceImplTest {

    @Mock
    private StoreService storeService;

    @Mock
    private ElevenlabsIntegrationService elevenlabsIntegrationService;

    @Mock
    private JingleRepository jingleRepository;

    @Mock
    private JingleGenerationRepository jingleGenerationRepository;

    @Mock
    private JingleSlotRepository jingleSlotRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private JingleServiceImpl jingleService;

    private final Organization organization = Organization.builder().companyName("Test Cafe").build();
    private final AppUser appUser = AppUser.builder().email("owner@example.com").organization(organization).build();

    @Test
    @DisplayName("At the monthly limit, nothing is generated (no ElevenLabs credit spent)")
    void createJingle_atLimit_refusesBeforeCallingElevenlabs() {
        when(jingleGenerationRepository.countAllByOrganizationAndCreatedAtGreaterThanEqual(eq(organization), any()))
            .thenReturn((long) JingleService.MONTHLY_LIMIT);

        var e = assertThrows(JingleCreationLimitExceededException.class, () -> jingleService.createJingle(appUser, dto()));

        assertTrue(e.getMessage().contains("resets on 1 "), e.getMessage());
        verify(elevenlabsIntegrationService, never()).getSpeechFileUrl(anyString(), anyString());
        verify(jingleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Below the limit, the jingle is saved and counted for this month")
    void createJingle_belowLimit_recordsGeneration() {
        when(jingleGenerationRepository.countAllByOrganizationAndCreatedAtGreaterThanEqual(eq(organization), any()))
            .thenReturn(19L);
        when(elevenlabsIntegrationService.getSpeechFileUrl(anyString(), anyString()))
            .thenReturn("https://weresona.com/files/a.mp3");

        jingleService.createJingle(appUser, dto());

        verify(jingleRepository).save(any());
        verify(jingleGenerationRepository).save(any(JingleGeneration.class));
    }

    @Test
    @DisplayName("The month is counted from the 1st, 00:00 in the app time zone")
    void monthlyCount_startsOnTheFirst() {
        var expectedStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        when(jingleGenerationRepository.countAllByOrganizationAndCreatedAtGreaterThanEqual(organization, expectedStart))
            .thenReturn(3L);

        assertTrue(jingleService.getJinglesCreatedThisMonth(appUser) == 3L);
    }


    @Test
    @DisplayName("Rescheduling drops upcoming plays and applies the new dates, without regenerating audio")
    void updateJingleSchedule() {
        var jingle = Jingle.builder()
            .startDate(LocalDateTime.now())
            .endDate(LocalDateTime.now().plusDays(1))
            .repeatingTime(JingleRepeatingTime.EVERY_HOUR)
            .build();
        ReflectionTestUtils.setField(jingle, "id", 18L);
        when(jingleRepository.findJingleByIdAndOrganization(18L, organization)).thenReturn(Optional.of(jingle));

        var newStart = LocalDateTime.now().plusDays(2).withNano(0);
        var newEnd = newStart.plusDays(5);
        jingleService.updateJingleSchedule(
            18L,
            new JingleScheduleUpdateDto(newStart, newEnd, JingleRepeatingTime.EVERY_HOUR),
            appUser
        );

        verify(jingleSlotRepository).deleteUpcoming(eq(18L), eq(JingleSlotStatus.PENDING), any(Instant.class));
        assertTrue(jingle.getStartDate().equals(newStart) && jingle.getEndDate().equals(newEnd));
        verify(elevenlabsIntegrationService, never()).getSpeechFileUrl(anyString(), anyString());
    }

    private static JingleCreateDto dto() {
        return new JingleCreateDto(
            JingleVoice.RACHEL,
            JingleCategory.PROMOTIONAL,
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusDays(1),
            JingleRepeatingTime.EVERY_HOUR,
            "Welcome!",
            1.0
        );
    }

}
