package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.config.props.MusicGenerationProps;
import kz.genvibe.media_management.model.entity.Music;
import kz.genvibe.media_management.model.entity.MusicGenerationJob;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import kz.genvibe.media_management.model.enums.MusicMood;
import kz.genvibe.media_management.repository.MusicGenerationJobRepository;
import kz.genvibe.media_management.repository.MusicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MusicPipelineTest {

    private static final byte[] AUDIO = new byte[2_880_000]; // 180 s at 128 kbps

    @Mock
    private MusicGenerationJobRepository jobRepository;
    @Mock
    private MusicRepository musicRepository;
    @Mock
    private MusicGenerator generator;
    @Mock
    private AudioInspector audioInspector;
    @Mock
    private MusicTrackStorage trackStorage;

    private final MusicGenerationProps props = new MusicGenerationProps();
    private final MusicPromptBuilder promptBuilder = new MusicPromptBuilder();
    private MusicGenerationService service;

    @BeforeEach
    void setUp() {
        service = new MusicGenerationService(jobRepository, musicRepository, promptBuilder, generator, audioInspector, trackStorage, props);
        when(generator.name()).thenReturn("elevenlabs");
        when(jobRepository.sumCostSince(any())).thenReturn(BigDecimal.ZERO);
        when(jobRepository.findAllByStatusAndStartedAtBefore(any(), any())).thenReturn(List.of());
        when(jobRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        when(trackStorage.save(any())).thenReturn("https://weresona.com/files/track-1.mp3");
    }

    // ---------- prompts ----------

    @Test
    @DisplayName("Every atmosphere and mood gets an instrumental brief with a tempo")
    void everyCombinationHasAPrompt() {
        for (var atmosphere : MusicAtmosphere.values()) {
            for (var mood : MusicMood.values()) {
                var prompt = promptBuilder.build(atmosphere, List.of(mood), 0);
                assertTrue(prompt.contains("Instrumental only, no vocals"), prompt);
                assertTrue(prompt.matches("(?s).*Around \\d{2,3} BPM.*"), prompt);
            }
        }
    }

    @Test
    @DisplayName("Relaxing moods are slower than energetic ones, and takes differ from each other")
    void moodChangesTempoAndTakesDiffer() {
        var relaxed = promptBuilder.build(MusicAtmosphere.WARM_AND_WELCOMING, List.of(MusicMood.SLOW_DOWN_AND_RELAX), 0);
        var energetic = promptBuilder.build(MusicAtmosphere.WARM_AND_WELCOMING, List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL), 0);
        assertTrue(relaxed.contains("Around 85 BPM"), relaxed);
        assertTrue(energetic.contains("Around 101 BPM"), energetic);

        var takes = new HashSet<String>();
        for (var variation = 0; variation < 6; variation++) {
            takes.add(promptBuilder.build(MusicAtmosphere.SOCIAL_AND_STYLISH, List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL), variation));
        }
        assertEquals(6, takes.size());
    }

    // ---------- automatic checks ----------

    @Test
    @DisplayName("ffmpeg's output is read into length, loudness and silences")
    void readsFfmpegOutput() {
        var report = AudioReport.parseFfmpegOutput("""
            Input #0, mp3, from 'track.mp3':
              Duration: 00:03:00.05, start: 0.025057, bitrate: 128 kb/s
            [silencedetect @ 0x1] silence_start: 0
            [silencedetect @ 0x1] silence_end: 2.1 | silence_duration: 2.1
            [silencedetect @ 0x1] silence_start: 60.5
            [silencedetect @ 0x1] silence_end: 65 | silence_duration: 4.5
            [silencedetect @ 0x1] silence_start: 178.6
            [Parsed_loudnorm_1 @ 0x2]
            {
                "input_i" : "-21.30",
                "input_tp" : "-3.10"
            }
            """);

        assertEquals(180.05, report.durationSeconds(), 0.001);
        assertEquals(-21.3, report.loudnessLufs(), 0.001);
        assertEquals(4.5, report.longestSilenceSeconds(), 0.001, "the fade-out at the end doesn't count");
        assertEquals(2.1, report.leadingSilenceSeconds(), 0.001);
        assertTrue(report.needsLoudnessFix());
        assertEquals(
            List.of("Loudness is -21.3 LUFS, target is -16", "Starts with 2.1 s of silence", "Contains 4.5 s of silence"),
            report.problems(180)
        );
    }

    @Test
    @DisplayName("A good track has no problems; a short or unchecked one is flagged")
    void problems() {
        assertEquals(List.of(), new AudioReport(178, -16.4, 0.0, 0.0).problems(180));
        assertEquals(List.of("Length is 95 s, asked for 180 s"), new AudioReport(95, -16.0, 0.0, 0.0).problems(180));
        assertEquals(List.of("The track is silent"), new AudioReport(180, -99.0, 0.0, 0.0).problems(180));

        var unchecked = AudioReport.estimatedFromSize(AUDIO.length);
        assertEquals(180, unchecked.durationSeconds(), 0.001);
        assertEquals(List.of("Loudness and silence were not checked (ffmpeg is not installed)"), unchecked.problems(180));
    }

    // ---------- queue, budget, review ----------

    @Test
    @DisplayName("Queued tracks continue the take numbers and cost nothing yet")
    void queueing() {
        when(jobRepository.countByAtmosphere(MusicAtmosphere.NATURAL_AND_MINDFUL)).thenReturn(4L);

        var jobs = service.queue(MusicAtmosphere.NATURAL_AND_MINDFUL, List.of(MusicMood.SLOW_DOWN_AND_RELAX), 3, "numi@weresona.com");

        assertEquals(List.of(4, 5, 6), jobs.stream().map(MusicGenerationJob::getVariation).toList());
        assertTrue(jobs.stream().allMatch(job -> job.getStatus() == MusicJobStatus.QUEUED && job.getCostUsd().signum() == 0));
        assertEquals(180, jobs.getFirst().getLengthSeconds());
        verify(generator, never()).generate(anyString(), anyInt());

        assertThrows(IllegalArgumentException.class, () -> service.queue(MusicAtmosphere.NATURAL_AND_MINDFUL, List.of(), 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> service.queue(MusicAtmosphere.NATURAL_AND_MINDFUL, List.of(MusicMood.SLOW_DOWN_AND_RELAX), 21, "x"));
    }

    @Test
    @DisplayName("A generated track is checked, stored, costed and left for review")
    void generatesAndWaitsForReview() {
        var job = queuedJob();
        when(generator.generate(job.getPrompt(), 180)).thenReturn(AUDIO);
        when(audioInspector.inspect(AUDIO)).thenReturn(new AudioInspector.Result(AUDIO, new AudioReport(180, -16.2, 0.0, 0.0), true));

        assertTrue(service.processNext());

        assertEquals(MusicJobStatus.IN_REVIEW, job.getStatus());
        assertEquals("https://weresona.com/files/track-1.mp3", job.getFileUrl());
        assertEquals(0, new BigDecimal("0.45").compareTo(job.getCostUsd()), "3 minutes at $0.15");
        assertEquals("Loudness was evened out. Passed the automatic checks.", job.getQualityNotes());
        assertEquals(1, job.getAttempts());
        verify(musicRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nothing is generated once the month's budget is used up")
    void budgetCap() {
        queuedJob();
        when(jobRepository.sumCostSince(any())).thenReturn(new BigDecimal("9.80"));

        assertFalse(service.processNext());

        verify(generator, never()).generate(anyString(), anyInt());
    }

    @Test
    @DisplayName("A refusal from the provider (plan, key, prompt) fails at once and costs nothing")
    void refusalIsNotRetried() {
        var job = queuedJob();
        when(generator.generate(anyString(), anyInt()))
            .thenThrow(new MusicGenerationException("ElevenLabs answered 402: paid_plan_required", false, null));

        service.processNext();

        assertEquals(MusicJobStatus.FAILED, job.getStatus());
        assertEquals(0, job.getCostUsd().signum());
        assertTrue(job.getLastError().contains("402"));
    }

    @Test
    @DisplayName("A passing problem is retried up to the limit, then the track fails")
    void temporaryProblemIsRetried() {
        var job = queuedJob();
        when(generator.generate(anyString(), anyInt()))
            .thenThrow(new MusicGenerationException("ElevenLabs answered 503", true, null));

        service.processNext();
        assertEquals(MusicJobStatus.QUEUED, job.getStatus());
        service.processNext();
        service.processNext();

        assertEquals(MusicJobStatus.FAILED, job.getStatus());
        assertEquals(3, job.getAttempts());
    }

    @Test
    @DisplayName("Approving puts the track in the library; rejecting doesn't")
    void review() {
        var job = queuedJob();
        job.setStatus(MusicJobStatus.IN_REVIEW);
        job.setFileUrl("https://weresona.com/files/track-1.mp3");
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job));
        when(musicRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.approve(7L);

        var saved = ArgumentCaptor.forClass(Music.class);
        verify(musicRepository).save(saved.capture());
        assertEquals(MusicAtmosphere.MODERN_AND_ENERGETIC, saved.getValue().getAtmosphere());
        assertEquals(List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL), saved.getValue().getMood());
        assertEquals("/assets/music/icon-energetic.svg", saved.getValue().getIconLocation());
        assertEquals(MusicJobStatus.APPROVED, job.getStatus());

        assertThrows(IllegalStateException.class, () -> service.reject(7L), "already reviewed");
    }

    private MusicGenerationJob queuedJob() {
        var moods = List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL);
        var job = new MusicGenerationJob(
            MusicAtmosphere.MODERN_AND_ENERGETIC, moods, 0,
            promptBuilder.build(MusicAtmosphere.MODERN_AND_ENERGETIC, moods, 0), 180, "elevenlabs", "numi@weresona.com"
        );
        when(jobRepository.findFirstByStatusOrderByIdAsc(MusicJobStatus.QUEUED))
            .thenAnswer(call -> job.getStatus() == MusicJobStatus.QUEUED ? Optional.of(job) : Optional.empty());
        when(jobRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        return job;
    }

}
