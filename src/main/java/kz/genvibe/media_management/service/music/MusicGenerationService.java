package kz.genvibe.media_management.service.music;

import jakarta.persistence.EntityNotFoundException;
import kz.genvibe.media_management.config.props.MusicGenerationProps;
import kz.genvibe.media_management.model.entity.Music;
import kz.genvibe.media_management.model.entity.MusicGenerationJob;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import kz.genvibe.media_management.model.enums.MusicMood;
import kz.genvibe.media_management.repository.MusicGenerationJobRepository;
import kz.genvibe.media_management.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * The AI music pipeline: tracks are queued, generated one at a time, checked,
 * stored, and then wait for a person to approve them. Only approved tracks join
 * the music library that stores play from.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MusicGenerationService {

    static final int MAX_TRACKS_PER_REQUEST = 20;
    /** A job still "generating" after this long was cut off by a restart. */
    private static final Duration STUCK_AFTER = Duration.ofMinutes(20);

    private static final Map<MusicAtmosphere, String> ICONS = Map.of(
        MusicAtmosphere.MODERN_AND_ENERGETIC, "/assets/music/icon-energetic.svg",
        MusicAtmosphere.SOCIAL_AND_STYLISH, "/assets/music/icon-energetic.svg",
        MusicAtmosphere.CREATIVE_AND_PLAYFUL, "/assets/music/icon-energetic.svg",
        MusicAtmosphere.PREMIUM_AND_SOPHISTICATED, "/assets/music/icon-cinematic.svg",
        MusicAtmosphere.TRADITIONAL_AND_CULTURAL_CHINESE, "/assets/music/icon-cinematic.svg",
        MusicAtmosphere.TRADITIONAL_AND_CULTURAL_JAPANESE, "/assets/music/icon-cinematic.svg",
        MusicAtmosphere.NATURAL_AND_MINDFUL, "/assets/music/icon-ambient.svg",
        MusicAtmosphere.WARM_AND_WELCOMING, "/assets/music/icon-chill.svg"
    );

    private final MusicGenerationJobRepository jobRepository;
    private final MusicRepository musicRepository;
    private final MusicPromptBuilder promptBuilder;
    private final MusicGenerator generator;
    private final AudioInspector audioInspector;
    private final MusicTrackStorage trackStorage;
    private final MusicGenerationProps props;

    /** What one track costs at the provider's price per minute. */
    public BigDecimal costPerTrack() {
        return props.getPricePerMinuteUsd()
            .multiply(BigDecimal.valueOf(props.getTrackSeconds()))
            .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal spentThisMonth() {
        var monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var spent = jobRepository.sumCostSince(monthStart);
        return spent == null ? BigDecimal.ZERO : spent;
    }

    public boolean budgetAllowsAnotherTrack() {
        return spentThisMonth().add(costPerTrack()).compareTo(props.getMonthlyBudgetUsd()) <= 0;
    }

    /** Adds tracks to the queue. Nothing is spent until the worker picks them up. */
    @Transactional
    public List<MusicGenerationJob> queue(MusicAtmosphere atmosphere, List<MusicMood> moods, int count, String requestedBy) {
        if (moods.isEmpty() || moods.size() > 2) throw new IllegalArgumentException("Choose one or two moods");
        if (count < 1 || count > MAX_TRACKS_PER_REQUEST) {
            throw new IllegalArgumentException("Queue between 1 and " + MAX_TRACKS_PER_REQUEST + " tracks at a time");
        }

        // Carry on from earlier takes so new tracks differ from the ones already made.
        var firstVariation = (int) jobRepository.countByAtmosphere(atmosphere);
        var jobs = new java.util.ArrayList<MusicGenerationJob>();
        for (var i = 0; i < count; i++) {
            var variation = firstVariation + i;
            jobs.add(new MusicGenerationJob(
                atmosphere, moods, variation,
                promptBuilder.build(atmosphere, moods, variation),
                props.getTrackSeconds(), generator.name(), requestedBy
            ));
        }
        return jobRepository.saveAll(jobs);
    }

    /**
     * Generates the next queued track, if there is one and the month's budget
     * allows it. Called by the background worker, one track at a time.
     *
     * @return true when a track was worked on
     */
    public boolean processNext() {
        requeueStuckJobs();

        var next = jobRepository.findFirstByStatusOrderByIdAsc(MusicJobStatus.QUEUED);
        if (next.isEmpty()) return false;
        if (!budgetAllowsAnotherTrack()) {
            log.info("Music generation paused: this month's budget of ${} is used up", props.getMonthlyBudgetUsd());
            return false;
        }

        var job = next.get();
        job.setStatus(MusicJobStatus.GENERATING);
        job.setAttempts(job.getAttempts() + 1);
        job.setStartedAt(Instant.now());
        job.setLastError(null);
        jobRepository.save(job);

        try {
            var audio = generator.generate(job.getPrompt(), job.getLengthSeconds());
            // The provider has charged for this track from here on, whatever happens next.
            job.setCostUsd(job.getCostUsd().add(costPerTrack()));

            var inspected = audioInspector.inspect(audio);
            var report = inspected.report();
            var problems = report.problems(job.getLengthSeconds());

            job.setFileUrl(trackStorage.save(inspected.audio()));
            job.setDurationSeconds(report.durationSeconds());
            job.setLoudnessLufs(report.loudnessLufs());
            job.setLongestSilenceSeconds(report.longestSilenceSeconds());
            job.setQualityNotes(notes(problems, inspected.loudnessFixed()));
            job.setStatus(MusicJobStatus.IN_REVIEW);
            log.info("Music job {} generated: {}", job.getId(), job.getQualityNotes());
        } catch (MusicGenerationException e) {
            var retry = e.isWorthRetrying() && job.getAttempts() < props.getMaxAttempts();
            job.setStatus(retry ? MusicJobStatus.QUEUED : MusicJobStatus.FAILED);
            job.setLastError(e.getMessage());
            log.warn("Music job {} {}: {}", job.getId(), retry ? "will be retried" : "failed", e.getMessage());
        } catch (RuntimeException e) {
            job.setStatus(MusicJobStatus.FAILED);
            job.setLastError(e.toString());
            log.error("Music job {} failed", job.getId(), e);
        }

        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
        return true;
    }

    /** Accepts a reviewed track into the music library, where store players pick it up. */
    @Transactional
    public Music approve(long jobId) {
        var job = inReview(jobId);

        var music = musicRepository.save(Music.builder()
            .fileUrl(job.getFileUrl())
            .atmosphere(job.getAtmosphere())
            .mood(new java.util.ArrayList<>(job.getMood()))
            .iconLocation(ICONS.get(job.getAtmosphere()))
            .build());

        job.setStatus(MusicJobStatus.APPROVED);
        job.setMusicId(music.getId());
        job.setReviewedAt(Instant.now());
        return music;
    }

    @Transactional
    public void reject(long jobId) {
        var job = inReview(jobId);
        job.setStatus(MusicJobStatus.REJECTED);
        job.setReviewedAt(Instant.now());
    }

    /** Puts a failed track back in the queue for a fresh set of attempts. */
    @Transactional
    public void retry(long jobId) {
        var job = find(jobId);
        if (job.getStatus() != MusicJobStatus.FAILED) throw new IllegalStateException("Only failed tracks can be retried");
        job.setStatus(MusicJobStatus.QUEUED);
        job.setAttempts(0);
    }

    /** Takes a track that hasn't been generated yet out of the queue. */
    @Transactional
    public void cancel(long jobId) {
        var job = find(jobId);
        if (job.getStatus() != MusicJobStatus.QUEUED) throw new IllegalStateException("Only queued tracks can be cancelled");
        jobRepository.delete(job);
    }

    @Transactional(readOnly = true)
    public List<MusicGenerationJob> recentJobs() {
        return jobRepository.findTop200ByOrderByIdDesc();
    }

    private void requeueStuckJobs() {
        var stuck = jobRepository.findAllByStatusAndStartedAtBefore(MusicJobStatus.GENERATING, Instant.now().minus(STUCK_AFTER));
        for (var job : stuck) {
            var retry = job.getAttempts() < props.getMaxAttempts();
            job.setStatus(retry ? MusicJobStatus.QUEUED : MusicJobStatus.FAILED);
            job.setLastError("Generation was interrupted (the app restarted)");
            jobRepository.save(job);
        }
    }

    private MusicGenerationJob inReview(long jobId) {
        var job = find(jobId);
        if (job.getStatus() != MusicJobStatus.IN_REVIEW) throw new IllegalStateException("This track is not waiting for review");
        return job;
    }

    private MusicGenerationJob find(long jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Music job not found"));
    }

    private static String notes(List<String> problems, boolean loudnessFixed) {
        var fixed = loudnessFixed ? "Loudness was evened out. " : "";
        return fixed + (problems.isEmpty() ? "Passed the automatic checks." : String.join(". ", problems) + ".");
    }

}
