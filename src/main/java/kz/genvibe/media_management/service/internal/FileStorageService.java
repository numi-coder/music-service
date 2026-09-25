package kz.genvibe.media_management.service.internal;

public interface FileStorageService {

    /**
     * Saves an MP3 file and returns a URL the store player can stream.
     * Uses Supabase Storage when configured, otherwise the local upload directory.
     */
    String saveMp3(byte[] content);

}
