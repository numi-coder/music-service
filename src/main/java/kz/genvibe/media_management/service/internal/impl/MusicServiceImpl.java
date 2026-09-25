package kz.genvibe.media_management.service.internal.impl;

import kz.genvibe.media_management.model.entity.Music;
import kz.genvibe.media_management.model.entity.Organization;
import kz.genvibe.media_management.repository.MusicRepository;
import kz.genvibe.media_management.service.internal.MusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MusicServiceImpl implements MusicService {

    private static final String MUSIC_PLACEHOLDER =
        "https://agnafyaipjhixqkijmwy.supabase.co/storage/v1/object/public/music/Warm%20and%20welcoming%20+%20slow%20down%20and%20relax.mp3";

    private final MusicRepository musicRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Music> getAllDistinctMusic() {
        return musicRepository.findDistinctBy();
    }

    @Override
    @Transactional(readOnly = true)
    public String getMusicPreviewUrl(Organization organization) {
        var atmosphereStr = organization.getMusicAtmosphere().name();
        var moodArray = organization.getMusicMood().stream()
            .map(Enum::name)
            .toArray(String[]::new);

        return musicRepository.findTopByAtmosphereAndMood(atmosphereStr, moodArray)
            .map(Music::getFileUrl)
            .orElse(MUSIC_PLACEHOLDER);
    }

}
