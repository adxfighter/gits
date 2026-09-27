package ru.gits.task.logistics.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteCacheHiddenTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** Test clock that only moves when the test says so. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-09-01T08:00:00Z");

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
    private final List<String> planned = new ArrayList<>();
    private int version;
    private final RoutePlanner planner = (from, to) -> {
        planned.add(from + "-" + to);
        return new Route(from, to, List.of("версия " + (++version)), 100 + version);
    };

    @Test
    void expiredRouteIsPlannedAgainEvenIfPopular() {
        var cache = new RouteCache(planner, clock, TTL, 100);
        Route first = cache.route("Москва", "Тверь");

        for (int minute = 0; minute < 30; minute++) {
            clock.advance(Duration.ofMinutes(1));
            cache.route("Москва", "Тверь");
        }

        assertThat(planned).hasSize(4);  // planned at 0, 10, 20 and 30 minutes
        assertThat(cache.route("Москва", "Тверь")).isNotEqualTo(first);
    }

    @Test
    void routeIsFreshUntilJustBeforeItsExpiry() {
        var cache = new RouteCache(planner, clock, TTL, 100);
        cache.route("Москва", "Тверь");

        clock.advance(TTL.minusMillis(1));
        cache.route("Москва", "Тверь");
        assertThat(planned).hasSize(1);

        clock.advance(Duration.ofMillis(1));  // exactly at the expiry moment
        cache.route("Москва", "Тверь");
        assertThat(planned).hasSize(2);
    }

    @Test
    void expiredRoutesDoNotCountInSize() {
        var cache = new RouteCache(planner, clock, TTL, 100);
        for (int i = 0; i < 50; i++) {
            cache.route("Москва", "Город-" + i);
        }
        assertThat(cache.size()).isEqualTo(50);

        clock.advance(TTL);

        assertThat(cache.size()).isZero();
    }

    @Test
    void capacityAndLeastRecentlyUsedEvictionAreKept() {
        var cache = new RouteCache(planner, clock, TTL, 2);

        cache.route("A", "B");
        cache.route("A", "C");
        cache.route("A", "B");
        cache.route("A", "D");  // evicts A-C, used least recently
        planned.clear();
        cache.route("A", "B");
        cache.route("A", "D");
        cache.route("A", "C");

        assertThat(planned).containsExactly("A-C");
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void replannedRouteGetsAFullNewLifetime() {
        var cache = new RouteCache(planner, clock, TTL, 100);
        cache.route("Москва", "Тверь");
        clock.advance(TTL.plusMinutes(1));
        cache.route("Москва", "Тверь");  // replanned at minute 11

        clock.advance(TTL.minusMinutes(1));  // minute 20: still fresh
        cache.route("Москва", "Тверь");

        assertThat(planned).hasSize(2);
    }
}
