package kz.genvibe.media_management.exception;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class JingleCreationLimitExceededException extends RuntimeException {

    private final int limit;
    private final LocalDate resetsOn;

    public JingleCreationLimitExceededException(String message, int limit, LocalDate resetsOn) {
        super(message);
        this.limit = limit;
        this.resetsOn = resetsOn;
    }

}
