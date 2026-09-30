package kz.genvibe.media_management.model.entity;

import jakarta.persistence.*;
import kz.genvibe.media_management.model.entity.base.BaseEntity;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import kz.genvibe.media_management.model.enums.MusicMood;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One AI-generated track, from request to review. */
@Entity
@Table(name = "music_generation_jobs")
@Getter
@Setter
@NoArgsConstructor
public class MusicGenerationJob extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private String requestedBy;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MusicAtmosphere atmosphere;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(length = 128, nullable = false)
    private List<MusicMood> mood = new ArrayList<>();

    /** Which take this is for its atmosphere and mood; it varies the prompt. */
    @Column(nullable = false)
    private int variation;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false)
    private int lengthSeconds;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private MusicJobStatus status = MusicJobStatus.QUEUED;

    @Column(nullable = false)
    private int attempts;

    /** What the provider charged for this track, all attempts together. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(length = 512)
    private String fileUrl;

    private Double durationSeconds;

    private Double loudnessLufs;

    private Double longestSilenceSeconds;

    /** What the automatic checks found, for the person reviewing. */
    @Column(columnDefinition = "TEXT")
    private String qualityNotes;

    /** The library track created when this job was approved. */
    private Long musicId;

    private Instant startedAt;

    private Instant finishedAt;

    private Instant reviewedAt;

    public MusicGenerationJob(
        MusicAtmosphere atmosphere,
        List<MusicMood> mood,
        int variation,
        String prompt,
        int lengthSeconds,
        String provider,
        String requestedBy
    ) {
        this.atmosphere = atmosphere;
        this.mood = new ArrayList<>(mood);
        this.variation = variation;
        this.prompt = prompt;
        this.lengthSeconds = lengthSeconds;
        this.provider = provider;
        this.requestedBy = requestedBy;
        this.createdAt = Instant.now();
    }

}
