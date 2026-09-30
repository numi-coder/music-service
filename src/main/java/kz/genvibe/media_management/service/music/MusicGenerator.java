package kz.genvibe.media_management.service.music;

/**
 * A company whose AI makes music for us. ElevenLabs is the first; another
 * provider is added by writing one more class like ElevenlabsMusicGenerator.
 */
public interface MusicGenerator {

    /** Short name stored with every track, e.g. "elevenlabs". */
    String name();

    /**
     * Makes one instrumental track as MP3. This call costs money.
     *
     * @throws MusicGenerationException when the provider refuses or fails
     */
    byte[] generate(String prompt, int lengthSeconds);

}
