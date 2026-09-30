package kz.genvibe.media_management.model.enums;

public enum MusicJobStatus {
    /** Waiting for its turn. */
    QUEUED,
    /** Being generated and checked right now. */
    GENERATING,
    /** Generated; waiting for a person to listen and decide. */
    IN_REVIEW,
    /** Accepted into the music library. */
    APPROVED,
    /** Listened to and turned down. */
    REJECTED,
    /** Could not be generated. */
    FAILED
}
