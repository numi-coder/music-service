package kz.genvibe.media_management.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RussianFirstCountriesTest {

    private final RussianFirstCountries countries = RussianFirstCountries.load();

    @Test
    @DisplayName("Addresses in post-Soviet countries are recognised, first and last of a range included")
    void recognisesCountries() {
        assertEquals(Optional.of(true), countries.contains("2.72.0.0"));
        assertEquals(Optional.of(true), countries.contains("2.75.255.255"));
        assertEquals(Optional.of(true), countries.contains(" 77.88.8.8 "));
        assertEquals(Optional.of(false), countries.contains("8.8.8.8"));
        assertEquals(Optional.of(false), countries.contains("1.1.1.1"));
        assertEquals(Optional.of(false), countries.contains("255.255.255.255"));
    }

    @Test
    @DisplayName("Private, IPv6 and broken addresses say nothing about the country")
    void unknownAddresses() {
        for (var address : new String[]{"127.0.0.1", "10.0.0.5", "192.168.31.1", "172.20.1.1", "::1", "2a02:6b8::1", "abc", "1.2.3", "999.1.1.1", ""}) {
            assertEquals(Optional.empty(), countries.contains(address), address);
        }
        assertEquals(Optional.empty(), countries.contains(null));
    }

}
