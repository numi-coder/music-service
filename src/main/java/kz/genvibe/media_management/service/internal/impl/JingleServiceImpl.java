package kz.genvibe.media_management.service.internal.impl;

import jakarta.persistence.EntityNotFoundException;
import kz.genvibe.media_management.exception.JingleCreationLimitExceededException;
import kz.genvibe.media_management.model.domain.PlayerCommand;
import kz.genvibe.media_management.model.domain.dto.jingle.JingleCreateDto;
import kz.genvibe.media_management.model.entity.*;
import kz.genvibe.media_management.model.enums.CommandType;
import kz.genvibe.media_management.model.enums.JingleSlotStatus;
import kz.genvibe.media_management.repository.JingleRepository;
import kz.genvibe.media_management.repository.JingleSlotRepository;
import kz.genvibe.media_management.service.integration.ElevenlabsIntegrationService;
import kz.genvibe.media_management.service.internal.JingleService;
import kz.genvibe.media_management.service.internal.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JingleServiceImpl implements JingleService {

    private final StoreService storeService;
    private final ElevenlabsIntegrationService elevenlabsIntegrationService;
    private final JingleRepository jingleRepository;
    private final JingleSlotRepository jingleSlotRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void createJingle(AppUser appUser, JingleCreateDto dto) {
        var organization = appUser.getOrganization();
        var jinglesCount = jingleRepository.countAllByOrganization(organization);

        if (jinglesCount >= 20) {
            throw new JingleCreationLimitExceededException("You exceeded the limit of 20");
        }

        var speechFileUrl = elevenlabsIntegrationService.getSpeechFileUrl(
            dto.announcementText(),
            dto.voice().getElevenlabsVoiceId()
        );

        var jingle = dto.toEntity();
        jingle.setOrganization(organization);
        jingle.setFileUrl(speechFileUrl);

        jingleRepository.save(jingle);

        log.info("Jingle created for organization: {}", appUser.getOrganization().getCompanyName());
    }

    @Override
    @Transactional
    public void deleteJingleById(long id, AppUser appUser) {
        jingleRepository.findJingleByIdAndOrganization(id, appUser.getOrganization())
            .orElseThrow(() -> new EntityNotFoundException("Jingle not found"));
        jingleSlotRepository.deleteByJingleId(id);
        jingleRepository.deleteStoreLinks(id);
        jingleRepository.hardDeleteById(id);
    }

    @Override
    @Transactional
    public void setPauseApprovalStatus(
        long id,
        AppUser appUser
    ) {
        var jingle = jingleRepository.findJingleByIdAndOrganization(id, appUser.getOrganization())
            .orElseThrow(() -> new EntityNotFoundException("Jingle not found"));
        jingle.setPauseApproved(true);
    }

    @Override
    @Transactional
    public void addJingleToStores(
        long id,
        List<Long> idList,
        AppUser appUser
    ) {
        var jingle = jingleRepository.findJingleByIdAndOrganization(id, appUser.getOrganization())
            .orElseThrow(() -> new EntityNotFoundException("Jingle not found"));
        var stores = storeService.getAllStoresByAppUserAndIdList(appUser, idList);

        jingle.getStores().addAll(stores);

        final var jingleSchedules = stores.stream()
            .map(Store::getJingleSchedule)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        var now = Instant.now();
        var zone = ZoneId.systemDefault();

        for (final var schedule : jingleSchedules) {
            generateSlotsForJingleOnDay(jingle, schedule, LocalDate.now(zone), zone, now);
        }
    }

    @Override
    @Transactional
    public void requestToPauseJingle(long id) {
        var jingle = jingleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Jingle not found"));
        jingle.setRequestedToPause(true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Jingle> getJingleHistory(AppUser appUser) {
        return jingleRepository.findJinglesByOrganization(appUser.getOrganization());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Jingle> getJingleRequestsToPause(AppUser appUser) {
        return jingleRepository.findJinglesByOrganizationAndRequestedToPauseIsTrue(appUser.getOrganization());
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void generateDailySlots() {
        var now = Instant.now();
        var zone = ZoneId.systemDefault();
        var today = LocalDate.now(zone);
        var startOfDay = today.atStartOfDay();
        var endOfDay = today.atTime(LocalTime.MAX);

        var jingles = jingleRepository.findActiveAssignedJingles(startOfDay, endOfDay);

        for (var jingle : jingles) {
            for (var store : jingle.getStores()) {
                var schedule = store.getJingleSchedule();
                if (schedule != null) {
                    generateSlotsForJingleOnDay(jingle, schedule, today, zone, now);
                }
            }
        }

        log.info("Daily slot generation finished for {} active jingle(s)", jingles.size());
    }

    private void generateSlotsForJingleOnDay(
        Jingle jingle,
        JingleSchedule schedule,
        LocalDate day,
        ZoneId zone,
        Instant notBefore
    ) {
        var startOfDay = day.atStartOfDay();
        var endOfDay = day.atTime(LocalTime.MAX);

        if (jingle.getStartDate().isAfter(endOfDay) || jingle.getEndDate().isBefore(startOfDay)) {
            return;
        }

        var interval = jingle.getRepeatingTime().getDuration().toMinutes();

        Set<Instant> existingTimes = schedule.getDailyJingleSlots().stream()
            .filter(slot -> Objects.equals(slot.getJingle().getId(), jingle.getId()))
            .map(JingleSlot::getPlayTime)
            .collect(Collectors.toSet());

        var slotTime = jingle.getStartDate();
        if (slotTime.isBefore(startOfDay)) {
            var steps = Duration.between(slotTime, startOfDay).toMinutes() / interval;
            slotTime = slotTime.plusMinutes(steps * interval);
            while (slotTime.isBefore(startOfDay)) {
                slotTime = slotTime.plusMinutes(interval);
            }
        }

        for (; !slotTime.isAfter(endOfDay) && !slotTime.isAfter(jingle.getEndDate()); slotTime = slotTime.plusMinutes(interval)) {
            var playInstant = slotTime.atZone(zone).toInstant();
            if (playInstant.isBefore(notBefore) || existingTimes.contains(playInstant)) {
                continue;
            }

            schedule.addSlot(JingleSlot.builder()
                .jingle(jingle)
                .jingleSchedule(schedule)
                .playTime(playInstant)
                .status(JingleSlotStatus.PENDING)
                .build());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void checkAndBroadcastJingles() {
        log.info("Starting broadcast");
        var now = Instant.now();
        var currentSlots = jingleSlotRepository.findJingleSlotsByPlayTimeLessThanEqualAndStatusAndJingleRequestedToPauseIsFalse(
            now,
            JingleSlotStatus.PENDING
        );

        for (var slot : currentSlots) {
            var storeId = slot.getJingleSchedule().getStore().getId();

            var command = new PlayerCommand(
                CommandType.PLAY_JINGLE,
                slot.getJingle().getFileUrl(),
                slot.getId(),
                slot.getJingle().getSpeed()
            );

            messagingTemplate.convertAndSend("/topic/store." + storeId + ".commands", command);

            slot.setStatus(JingleSlotStatus.PLAYED);
        }

        if (!currentSlots.isEmpty()) {
            log.info("Broadcasted {} jingles at {}", currentSlots.size(), now);
        }
    }

}
