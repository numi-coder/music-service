package kz.genvibe.media_management.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * Tells whether a visitor's internet address belongs to one of the countries where
 * the platform opens in Russian first (the post-Soviet countries). The address
 * list is geo/russian-first-ipv4.txt, rebuilt by scripts/update-russian-first-ranges.mjs.
 */
public final class RussianFirstCountries {

    private static final String RANGES_FILE = "geo/russian-first-ipv4.txt";

    private final long[] starts;
    private final long[] ends;

    private RussianFirstCountries(long[] starts, long[] ends) {
        this.starts = starts;
        this.ends = ends;
    }

    public static RussianFirstCountries load() {
        var starts = new ArrayList<Long>();
        var ends = new ArrayList<Long>();
        try (var in = RussianFirstCountries.class.getClassLoader().getResourceAsStream(RANGES_FILE)) {
            if (in == null) throw new IllegalStateException(RANGES_FILE + " is missing");
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var range = line.trim().split("-");
                starts.add(toNumber(range[0]).orElseThrow());
                ends.add(toNumber(range[1]).orElseThrow());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new RussianFirstCountries(
            starts.stream().mapToLong(Long::longValue).toArray(),
            ends.stream().mapToLong(Long::longValue).toArray()
        );
    }

    /**
     * Empty when the address says nothing about the country: not an IPv4 address,
     * or one from a private network (a test on the same machine or office network).
     */
    public Optional<Boolean> contains(String address) {
        return toNumber(address).filter(ip -> !isPrivate(ip)).map(ip -> {
            var index = Arrays.binarySearch(starts, ip);
            if (index >= 0) return true;
            var before = -index - 2;
            return before >= 0 && ip <= ends[before];
        });
    }

    private static boolean isPrivate(long ip) {
        var first = ip >> 24;
        var second = (ip >> 16) & 0xFF;
        return first == 10 || first == 127 || first == 0
            || (first == 172 && second >= 16 && second <= 31)
            || (first == 192 && second == 168)
            || (first == 169 && second == 254);
    }

    private static Optional<Long> toNumber(String address) {
        if (address == null) return Optional.empty();
        var parts = address.trim().split("\\.");
        if (parts.length != 4) return Optional.empty();
        long number = 0;
        for (var part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) return Optional.empty();
            var value = Integer.parseInt(part);
            if (value > 255) return Optional.empty();
            number = number * 256 + value;
        }
        return Optional.of(number);
    }

}
