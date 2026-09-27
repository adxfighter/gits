package ru.gits.task.telecom.t03;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Caches data sessions with a size limit and a time to live, and keeps usage statistics.
 * Used by a single gateway thread.
 */
public final class SessionCache {

    private record Entry(Session session, Instant expiresAt) {
    }

    private final SessionStore store;
    private final Clock clock;
    private final Duration ttl;
    private final int capacity;
    private final Map<String, Entry> entries;
    private final Map<String, Long> requestsBySession = new HashMap<>();
    private long hits;
    private long misses;

    public SessionCache(SessionStore store, Clock clock, Duration ttl, int capacity) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero() || capacity <= 0) {
            throw new IllegalArgumentException("ttl and capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > SessionCache.this.capacity;
            }
        };
    }

    /** The session, loaded from the store on a miss or after its cached copy expired. */
    public Optional<Session> session(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Instant now = clock.instant();
        requestsBySession.merge(sessionId, 1L, Long::sum);
        Entry cached = entries.get(sessionId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            hits++;
            return Optional.of(cached.session());
        }
        misses++;
        entries.remove(sessionId);
        Optional<Session> loaded = store.find(sessionId);
        loaded.ifPresent(session -> entries.put(sessionId, new Entry(session, now.plus(ttl))));
        return loaded;
    }

    /** Sessions with the most requests, most active first (ties by id). */
    public List<String> mostActive(int n) {
        return requestsBySession.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Number of sessions the per-session statistics currently track. */
    public int trackedSessions() {
        return requestsBySession.size();
    }

    public long hits() {
        return hits;
    }

    public long misses() {
        return misses;
    }

    public int size() {
        return entries.size();
    }
}
