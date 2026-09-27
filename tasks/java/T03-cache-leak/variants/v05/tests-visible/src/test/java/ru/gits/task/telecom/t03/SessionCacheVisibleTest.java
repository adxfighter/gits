package ru.gits.task.telecom.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SessionCacheVisibleTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    private final SessionStore store = id -> Optional.of(new Session(id, "79001234567", 10_000));

    @Test
    void secondRequestIsServedFromTheCache() {
        var cache = new SessionCache(store, clock, Duration.ofMinutes(5), 100);

        cache.session("S-1");
        cache.session("S-1");

        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void mostActiveSessionsComeFirst() {
        var cache = new SessionCache(store, clock, Duration.ofMinutes(5), 100);

        cache.session("S-1");
        cache.session("S-2");
        cache.session("S-2");

        assertThat(cache.mostActive(2)).containsExactly("S-2", "S-1");
    }
}
