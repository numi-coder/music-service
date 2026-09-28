package kz.genvibe.media_management.templates;

import kz.genvibe.media_management.model.domain.dto.store.ActiveStoreDto;
import kz.genvibe.media_management.model.entity.*;
import kz.genvibe.media_management.model.entity.analytics.JingleAggregateAnalyticsData;
import kz.genvibe.media_management.model.entity.analytics.JingleTypeDistributionData;
import kz.genvibe.media_management.model.entity.analytics.MusicAnalyticsData;
import kz.genvibe.media_management.model.entity.analytics.StoreAggregateAnalyticsData;
import kz.genvibe.media_management.model.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Renders the dashboard pages with sample data into static HTML files, to check
 * layouts (e.g. on phone screens) without a database. Only runs when
 * RESONA_SNAPSHOTS is set to an output directory.
 */
@EnabledIfEnvironmentVariable(named = "RESONA_SNAPSHOTS", matches = ".+")
class PageSnapshots {

    @Test
    void renderPages() throws Exception {
        var out = Path.of(System.getenv("RESONA_SNAPSHOTS"));
        Files.createDirectories(out);

        var organization = Organization.builder()
            .companyName("Marina Bay Coffee Co.")
            .businessType(BusinessType.values()[0])
            .musicProvider(MusicProvider.values()[0])
            .musicAtmosphere(MusicAtmosphere.MODERN_AND_ENERGETIC)
            .musicMood(new ArrayList<>(List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL)))
            .playtimeWindow(PlaytimeWindow.values()[0])
            .build();
        ReflectionTestUtils.setField(organization, "id", 3L);

        var stores = new ArrayList<Store>();
        for (int i = 1; i <= 3; i++) {
            var store = new Store();
            ReflectionTestUtils.setField(store, "id", (long) i);
            store.setName(i == 1 ? "Orchard Road flagship store" : "Store " + i);
            store.setLocation(i + " Raffles Place, Singapore 048616");
            store.setEmail("store" + i + "@example.com");
            store.setActive(i != 3);
            if (i != 3) store.setMusicLink("https://weresona.com/stores/" + i + "/cd6c12f9-7a02-4362-9930-72888e89529" + i);
            store.setLastAccessDate(LocalDate.now());
            stores.add(store);
        }
        organization.getStores().addAll(stores);

        var jingle = Jingle.builder()
            .voice(JingleVoice.RACHEL).category(JingleCategory.PROMOTIONAL)
            .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(7))
            .repeatingTime(JingleRepeatingTime.EVERY_HOUR)
            .announcementText("Welcome to Marina Bay Coffee! Try our new iced oat latte, 20% off this week.")
            .fileUrl("/assets/none.mp3").stores(new HashSet<>(stores.subList(0, 1)))
            .build();
        ReflectionTestUtils.setField(jingle, "id", 18L);
        organization.getJingles().add(jingle);

        var musics = new ArrayList<Music>();
        for (var atmosphere : List.of(MusicAtmosphere.MODERN_AND_ENERGETIC, MusicAtmosphere.WARM_AND_WELCOMING, MusicAtmosphere.PREMIUM_AND_SOPHISTICATED)) {
            musics.add(Music.builder().atmosphere(atmosphere).mood(new ArrayList<>(List.of(MusicMood.FEEL_ENERGIZED_AND_SOCIAL)))
                .fileUrl("/assets/none.mp3").iconLocation("/assets/music/icon-energetic.svg").build());
        }

        var appUser = AppUser.builder().email("owner@example.com").fullName("Numi").companyRole("Owner")
            .role(UserRole.ROLE_ADMIN).organization(organization).build();

        var musicData = instance(MusicAnalyticsData.class);
        var weekly = new LinkedHashMap<String, Integer>();
        var sample = new int[]{38, 42, 51, 47, 63, 72, 55};
        var days = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
        for (int d = 0; d < days.size(); d++) weekly.put(days.get(d), sample[d]);
        ReflectionTestUtils.setField(musicData, "weeklyPlays", weekly);
        ReflectionTestUtils.setField(musicData, "totalPlays", 318);
        ReflectionTestUtils.setField(musicData, "totalPlaysGrowthPercentage", 12);
        ReflectionTestUtils.setField(musicData, "averageDailyPlays", 45);
        ReflectionTestUtils.setField(musicData, "topStoreName", "Orchard Road flagship store");
        ReflectionTestUtils.setField(musicData, "topStorePlaysGrowthPercentage", 8);

        var jingleData = instance(JingleAggregateAnalyticsData.class);
        fillAggregate(jingleData);
        var storeData = instance(StoreAggregateAnalyticsData.class);
        fillAggregate(storeData);
        var distribution = instance(JingleTypeDistributionData.class);
        ReflectionTestUtils.setField(distribution, "category", JingleCategory.PROMOTIONAL);
        ReflectionTestUtils.setField(distribution, "totalPlays", 120);
        ReflectionTestUtils.setField(distribution, "percentage", 64);

        var activeStores = List.of(new ActiveStoreDto(1, "Orchard Road flagship store"), new ActiveStoreDto(2, "Store 2"));

        var common = Map.<String, Object>of("organization", organization, "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "t"));

        write(out, "dashboard", "pages/dashboard", common, Map.of(
            "stores", stores, "activeStores", activeStores, "dailyPlaysAndMinutes", musicData,
            "musicPreviewUrl", "/assets/none.mp3"));
        write(out, "enhanced-dashboard", "pages/enhanced-dashboard", common, Map.of(
            "stores", stores, "activeStores", activeStores, "dailyPlaysAndMinutes", musicData,
            "musicPreviewUrl", "/assets/none.mp3"));
        write(out, "music", "pages/music", common, Map.of(
            "musicAtmospheres", MusicAtmosphere.values(), "musicMoods", MusicMood.values(),
            "musics", musics, "moodNames", organization.getMoodNames()));
        write(out, "stores", "pages/stores", common, Map.of("stores", stores));
        write(out, "jingles", "pages/jingles", common, Map.of(
            "jingleVoice", JingleVoice.values(), "jingleCategory", JingleCategory.values(),
            "jingleRepeatingTime", JingleRepeatingTime.values(), "jingleHistory", List.of(jingle),
            "jinglesUsedThisMonth", 7L, "monthlyJingleLimit", 20, "jingleRequestsToPause", List.of(jingle),
            "activeStores", activeStores));
        write(out, "analytics", "pages/analytics", common, Map.of(
            "jingleData", jingleData, "musicData", musicData, "storeData", storeData,
            "jingleDistributionData", distribution, "activeStores", activeStores, "stores", stores));
        write(out, "settings", "pages/settings", common, Map.of(
            "appUser", appUser, "musicAtmospheres", MusicAtmosphere.values()));
        write(out, "store-player", "pages/store-dashboard", common, Map.of(
            "store", stores.get(0), "activeJingles", List.of(jingle)));
    }

    private static void fillAggregate(Object data) {
        ReflectionTestUtils.setField(data, "totalJingleBroadcasts", 842);
        ReflectionTestUtils.setField(data, "totalJingleBroadcastsWeekGrowth", 9);
        ReflectionTestUtils.setField(data, "completionRate", 97);
        ReflectionTestUtils.setField(data, "mostPlayedJingleName", "Iced oat latte promo");
        ReflectionTestUtils.setField(data, "mostPlayedJinglePlayCount", 210);
        ReflectionTestUtils.setField(data, "scheduledJingleTaskName", "Closing time");
        ReflectionTestUtils.setField(data, "scheduledJingleTaskCount", 3);
    }

    private static <T> T instance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void write(Path out, String name, String template, Map<String, Object> common, Map<String, Object> model)
        throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        var servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        var context = new WebContext(exchange);
        context.setVariables(common);
        context.setVariables(model);

        Files.writeString(out.resolve(name + ".html"), engine.process(template, context));
    }

}
