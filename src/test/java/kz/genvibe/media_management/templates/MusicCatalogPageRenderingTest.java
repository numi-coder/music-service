package kz.genvibe.media_management.templates;

import kz.genvibe.media_management.config.I18n;
import kz.genvibe.media_management.model.entity.MusicGenerationJob;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicJobStatus;
import kz.genvibe.media_management.model.enums.MusicMood;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicCatalogPageRenderingTest {

    @Test
    void showsSpendQueueAndReviewButtons() throws Exception {
        var moods = List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL, MusicMood.FEEL_COMFORTABLE_STAYING_LONGER);
        var inReview = job(12L, MusicJobStatus.IN_REVIEW, moods);
        inReview.setFileUrl("https://weresona.com/files/track-1.mp3");
        inReview.setDurationSeconds(180.05);
        inReview.setLoudnessLufs(-16.2);
        inReview.setCostUsd(new BigDecimal("0.4500"));
        inReview.setQualityNotes("Loudness was evened out. Passed the automatic checks.");
        var failed = job(11L, MusicJobStatus.FAILED, moods);
        failed.setLastError("ElevenLabs answered 402: paid_plan_required");
        var queued = job(13L, MusicJobStatus.QUEUED, moods);

        var html = render(Map.ofEntries(
            Map.entry("jobs", List.of(queued, inReview, failed)),
            Map.entry("atmospheres", MusicAtmosphere.values()),
            Map.entry("moods", MusicMood.values()),
            Map.entry("enabled", false),
            Map.entry("spent", new BigDecimal("0.4500")),
            Map.entry("budget", new BigDecimal("10")),
            Map.entry("costPerTrack", new BigDecimal("0.4500")),
            Map.entry("trackSeconds", 180),
            Map.entry("queued", 1L),
            Map.entry("inReview", 1L),
            Map.entry("approved", 0L)
        ));

        var snapshots = System.getenv("RESONA_SNAPSHOTS");
        if (snapshots != null) Files.writeString(Path.of(snapshots, "operator-music.html"), html);

        assertTrue(html.contains("$<span>0.45</span>"), html);
        assertTrue(html.contains("of $<span>10.00</span> budget"));
        assertTrue(html.contains("Queued tracks wait until it is switched on."));
        assertTrue(html.contains("action=\"/operator/music/12/approve\""));
        assertTrue(html.contains("action=\"/operator/music/12/reject\""));
        assertTrue(html.contains("action=\"/operator/music/11/retry\""));
        assertTrue(html.contains("action=\"/operator/music/13/cancel\""));
        assertFalse(html.contains("action=\"/operator/music/11/approve\""), "a failed track can't be approved");
        assertTrue(html.contains("src=\"https://weresona.com/files/track-1.mp3\""));
        assertTrue(html.contains("-16.2 LUFS"));
        assertTrue(html.contains("paid_plan_required"));
        assertTrue(html.contains("Feel energized and social + Feel comfortable staying longer"));
        assertFalse(html.contains("??"));
    }

    private static MusicGenerationJob job(long id, MusicJobStatus status, List<MusicMood> moods) {
        var job = new MusicGenerationJob(MusicAtmosphere.MODERN_AND_ENERGETIC, moods, 0, "Modern upbeat electronic pop.", 180, "elevenlabs", "numi@weresona.com");
        ReflectionTestUtils.setField(job, "id", id);
        job.setStatus(status);
        return job;
    }

    private static String render(Map<String, Object> model) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(I18n.messageSource());

        var servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        var context = new WebContext(exchange);
        context.setVariables(model);
        return engine.process("pages/operator/music-catalog", context);
    }

}
