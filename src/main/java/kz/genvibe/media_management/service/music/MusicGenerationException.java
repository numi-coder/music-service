package kz.genvibe.media_management.service.music;

import lombok.Getter;

@Getter
public class MusicGenerationException extends RuntimeException {

    /** False when trying again can't help: the plan doesn't allow it, the key is wrong, the prompt was refused. */
    private final boolean worthRetrying;

    public MusicGenerationException(String message, boolean worthRetrying, Throwable cause) {
        super(message, cause);
        this.worthRetrying = worthRetrying;
    }

}
