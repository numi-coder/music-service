package kz.genvibe.media_management.service.music;

import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicMood;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a business's atmosphere and mood choices into the brief sent to the music
 * provider. The variation number gives different takes on the same choices, so a
 * catalog doesn't end up with near-identical tracks.
 *
 * Briefs describe styles and instruments only. They must never name an artist,
 * song, album or label: the providers' terms forbid it.
 */
@Component
public class MusicPromptBuilder {

    private record Style(String genre, String instruments, int slowestBpm, int fastestBpm) {}

    private record Feel(String description, int tempoShift) {}

    private static final Map<MusicAtmosphere, Style> STYLES = Map.of(
        MusicAtmosphere.WARM_AND_WELCOMING, new Style(
            "warm acoustic pop with a soft folk touch",
            "acoustic guitar, upright piano, brushed drums, gentle bass", 85, 100),
        MusicAtmosphere.MODERN_AND_ENERGETIC, new Style(
            "modern upbeat electronic pop with a nu-disco feel",
            "punchy drums, synth bass, bright synths, rhythm guitar", 108, 122),
        MusicAtmosphere.PREMIUM_AND_SOPHISTICATED, new Style(
            "elegant lounge jazz and refined downtempo",
            "grand piano, double bass, soft brushed drums, subtle strings", 70, 92),
        MusicAtmosphere.SOCIAL_AND_STYLISH, new Style(
            "stylish deep house with funk-tinged nu-disco",
            "groovy bass, crisp percussion, electric piano, muted guitar", 105, 120),
        MusicAtmosphere.CREATIVE_AND_PLAYFUL, new Style(
            "playful indie pop with quirky funk",
            "plucky guitars, handclaps, marimba, bouncy bass", 100, 118),
        MusicAtmosphere.NATURAL_AND_MINDFUL, new Style(
            "organic ambient and gentle neoclassical",
            "felt piano, soft pads, acoustic guitar, light airy textures", 60, 80),
        MusicAtmosphere.TRADITIONAL_AND_CULTURAL_CHINESE, new Style(
            "contemporary instrumental music built on traditional Chinese instruments",
            "guzheng, erhu, dizi bamboo flute, soft percussion", 70, 95),
        MusicAtmosphere.TRADITIONAL_AND_CULTURAL_JAPANESE, new Style(
            "contemporary instrumental music built on traditional Japanese instruments",
            "koto, shakuhachi, shamisen, soft taiko accents", 65, 90)
    );

    private static final Map<MusicMood, Feel> FEELS = Map.of(
        MusicMood.SLOW_DOWN_AND_RELAX, new Feel("calm, unhurried and soothing, low intensity", -8),
        MusicMood.FEEL_COMFORTABLE_STAYING_LONGER, new Feel("cozy, easygoing, steady and unobtrusive", -3),
        MusicMood.FEEL_ENERGIZED_AND_SOCIAL, new Feel("lively, feel-good and sociable, medium-high energy", 8),
        MusicMood.FEEL_LUXURIOUS_AND_SOPHISTICATED, new Feel("refined, polished, spacious and understated", -4),
        MusicMood.MOVE_EFFICIENTLY_AND_DECIDE_QUICKLY, new Feel("brisk and driving, with focused momentum", 12)
    );

    private static final List<String> TAKES = List.of(
        "Led by a memorable melodic hook.",
        "Groove-led, with only a light melody.",
        "A sparser arrangement with plenty of space.",
        "A fuller arrangement with layered textures.",
        "A slightly brighter, sunnier tone.",
        "A slightly mellower, warmer tone."
    );

    private static final int[] TEMPO_NUDGES = {0, 4, -4, 2, -2};

    public String build(MusicAtmosphere atmosphere, List<MusicMood> moods, int variation) {
        var style = STYLES.get(atmosphere);
        var feels = moods.stream().map(FEELS::get).toList();

        var feel = feels.stream().map(Feel::description).collect(Collectors.joining("; "));
        var take = TAKES.get(Math.floorMod(variation, TAKES.size()));

        return "%s. Instruments: %s. Mood: %s. Around %d BPM. %s ".formatted(
            capitalize(style.genre()), style.instruments(), feel, tempo(style, feels, variation), take
        ) + "Instrumental only, no vocals. Background music for a business space: even volume throughout, "
            + "no long intro, no sudden drops or silences, and a smooth ending.";
    }

    private static int tempo(Style style, List<Feel> feels, int variation) {
        var middle = (style.slowestBpm() + style.fastestBpm()) / 2.0;
        var shift = feels.stream().mapToInt(Feel::tempoShift).average().orElse(0);
        var nudge = TEMPO_NUDGES[Math.floorMod(variation, TEMPO_NUDGES.length)];
        return (int) Math.round(middle + shift + nudge);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

}
