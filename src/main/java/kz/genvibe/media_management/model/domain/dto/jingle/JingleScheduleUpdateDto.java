package kz.genvibe.media_management.model.domain.dto.jingle;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import kz.genvibe.media_management.model.enums.JingleRepeatingTime;

import java.time.LocalDateTime;

public record JingleScheduleUpdateDto(
    @NotNull LocalDateTime startDate,
    @NotNull @Future LocalDateTime endDate,
    @NotNull JingleRepeatingTime repeatingTime
) {
    @AssertTrue(message = "{validation.schedule.end_after_start}")
    public boolean isEndAfterStart() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
