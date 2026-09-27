package ru.gits.task.shop.t09;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Per-client rate limits of the partner API. Called concurrently by web server threads.
 */
public final class ApiRateLimiter {

    private final long capacity;
    private final long tokensPerSecond;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public ApiRateLimiter(long capacity, long tokensPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /** Takes one token from the client's bucket; the bucket is created full on the first request. */
    public boolean tryAcquire(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                id -> new TokenBucket(capacity, tokensPerSecond, nanoClock));
        return bucket.tryAcquire();
    }
}
