package kz.genvibe.media_management.model.domain.dto.onboarding;

import jakarta.validation.constraints.NotEmpty;
import kz.genvibe.media_management.model.enums.MusicMood;

import java.util.List;

public record Step4Dto(
    @NotEmpty List<MusicMood> mood
) { }
