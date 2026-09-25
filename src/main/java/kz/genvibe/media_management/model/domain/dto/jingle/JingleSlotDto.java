package kz.genvibe.media_management.model.domain.dto.jingle;

import java.time.Instant;

public record JingleSlotDto(
    long id,
    Instant playTime,
    String fileUrl
) { }
