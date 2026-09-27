package ru.gits.task.telecom.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SessionCacheHiddenTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-09-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final Set<String> endedSessions = new HashSet<>();
    private final SessionStore store = id -> endedSessions.contains(id)
            ? Optional.empty()
            : Optional.of(new Session(id, "7900" + id.hashCode(), 5_000));

    @Test
    void statisticsDoNotGrowBeyondTheCachedSessions() {
        var cache = new SessionCache(store, clock, TTL, 100);

        for (int i = 0; i < 100_000; i++) {
            cache.session("S-" + i);
        }

        assertThat(cache.size()).isEqualTo(100);
        assertThat(cache.trackedSessions()).isLessThanOrEqualTo(100);
    }

    @Test
    void evictedSessionsDisappearFromMostActive() {
        var cache = new SessionCache(store, clock, TTL, 2);
        for (int i = 0; i < 5; i++) {
            cache.session("HEAVY");
        }
        cache.session("B");
        cache.session("C");  // evicts HEAVY, the least recently used

        assertThat(cache.mostActive(10)).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void endedSessionsAreNotTracked() {
        var cache = new SessionCache(store, clock, TTL, 100);
        endedSessions.add("GONE");

        assertThat(cache.session("GONE")).isEmpty();
        assertThat(cache.session("GONE")).isEmpty();

        assertThat(cache.trackedSessions()).isZero();
        assertThat(cache.misses()).isEqualTo(2);
        assertThat(cache.mostActive(5)).isEmpty();
    }

    @Test
    void sessionEndingAfterExpiryIsForgotten() {
        var cache = new SessionCache(store, clock, TTL, 100);
        cache.session("S-1");
        cache.session("S-1");
        cache.session("S-2");

        endedSessions.add("S-1");
        clock.advance(TTL);
        cache.session("S-2");  // S-2 is reloaded and fresh

        assertThat(cache.session("S-1")).isEmpty();
        assertThat(cache.mostActive(5)).containsExactly("S-2");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void totalCountersStayExactAcrossEvictionsAndExpiry() {
        var cache = new SessionCache(store, clock, TTL, 10);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 50; i++) {
                cache.session("S-" + i);  // 50 distinct sessions, only 10 fit: all misses
                cache.session("S-" + i);  // immediately again: hit
            }
            clock.advance(TTL);
        }

        assertThat(cache.misses()).isEqualTo(150);
        assertThat(cache.hits()).isEqualTo(150);
        assertThat(cache.trackedSessions()).isLessThanOrEqualTo(10);
    }

    @Test
    void activityOfCachedSessionsIsCountedAcrossReloads() {
        var cache = new SessionCache(store, clock, TTL, 10);
        cache.session("S-1");
        cache.session("S-1");
        cache.session("S-2");
        clock.advance(TTL);  // S-1 is reloaded on the next request, it is still active

        cache.session("S-1");
        cache.session("S-2");

        assertThat(cache.mostActive(1)).containsExactly("S-1");
    }
}
