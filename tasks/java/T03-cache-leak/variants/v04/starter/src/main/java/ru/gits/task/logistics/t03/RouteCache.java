package ru.gits.task.logistics.t03;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Caches planned routes for a limited time and up to a limited number of routes.
 * Used by a single planning thread.
 */
public final class RouteCache {

    private record Key(String from, String to) {
    }

    private record Entry(Route route, Instant expiresAt) {
    }

    private final RoutePlanner planner;
    private final Clock clock;
    private final Duration ttl;
    private final int capacity;
    private final Map<Key, Entry> entries;

    public RouteCache(RoutePlanner planner, Clock clock, Duration ttl, int capacity) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero() || capacity <= 0) {
            throw new IllegalArgumentException("ttl and capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
                return size() > RouteCache.this.capacity;
            }
        };
    }

    /** Route between two cities; planned again when there is no cached route. */
    public Route route(String from, String to) {
        Key key = new Key(from, to);
        Entry cached = entries.get(key);
        if (cached != null) {
            return cached.route();
        }
        Route planned = planner.plan(from, to);
        store(key, planned);
        return planned;
    }

    /** Number of routes currently held by the cache. */
    public int size() {
        return entries.size();
    }

    private void store(Key key, Route route) {
        Instant now = clock.instant();
        removeExpired(now);
        entries.put(key, new Entry(route, now.plus(ttl)));
    }

    private void removeExpired(Instant now) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().expiresAt())) {
                iterator.remove();
            }
        }
    }
}
