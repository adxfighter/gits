package ru.gits.api.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** In-memory fixed-window rate limiter; sufficient for a single api instance. */
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws 429 when {@code key} exceeded {@code limit} calls within the current window. */
    public void check(String bucket, String key, int limit, Duration window) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.startedAt + window.toMillis() <= now);
        }
        Window current = windows.compute(bucket + "|" + key, (k, existing) ->
                existing == null || existing.startedAt + window.toMillis() <= now
                        ? new Window(now, 1)
                        : new Window(existing.startedAt, existing.count + 1));
        if (current.count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток, повторите позже");
        }
    }

    private record Window(long startedAt, int count) {
    }
}
