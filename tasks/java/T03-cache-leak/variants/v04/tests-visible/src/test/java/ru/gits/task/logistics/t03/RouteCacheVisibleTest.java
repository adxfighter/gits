package ru.gits.task.logistics.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteCacheVisibleTest {

    private final List<String> planned = new ArrayList<>();
    private final RoutePlanner planner = (from, to) -> {
        planned.add(from + "-" + to);
        return new Route(from, to, List.of("Владимир"), 400);
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void routeIsPlannedOnceWhileCached() {
        var cache = new RouteCache(planner, clock, Duration.ofMinutes(10), 100);

        cache.route("Москва", "Нижний Новгород");
        cache.route("Москва", "Нижний Новгород");

        assertThat(planned).containsExactly("Москва-Нижний Новгород");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void directionMatters() {
        var cache = new RouteCache(planner, clock, Duration.ofMinutes(10), 100);

        cache.route("Москва", "Казань");
        cache.route("Казань", "Москва");

        assertThat(planned).containsExactly("Москва-Казань", "Казань-Москва");
    }
}
