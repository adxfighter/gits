package ru.gits.task.telecom.t09;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Token bucket limiting SMS sent through the gateway by one customer. Not thread-safe.
 */
public final class SmsRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long capacity;
    private final long tokensPerMinute;
    private final LongSupplier nanoClock;

    private long tokens;
    private long lastRefillNanos;

    /**
     * @param capacity        maximum number of tokens; the bucket starts full
     * @param tokensPerMinute refill rate, tokens are added uniformly including fractions
     * @param nanoClock       time source in nanoseconds, like {@link System#nanoTime()}
     */
    public SmsRateLimiter(long capacity, long tokensPerMinute, LongSupplier nanoClock) {
        if (capacity <= 0 || tokensPerMinute <= 0) {
            throw new IllegalArgumentException("capacity and rate must be positive");
        }
        this.capacity = capacity;
        this.tokensPerMinute = tokensPerMinute;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.tokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Takes one token if there is one. */
    public boolean tryAcquire() {
        refill();
        if (tokens >= 1) {
            tokens--;
            return true;
        }
        return false;
    }

    /** Whole tokens available now. */
    public long available() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsedSeconds = (now - lastRefillNanos) / NANOS_PER_SECOND;
        long added = elapsedSeconds * tokensPerMinute / 60;
        tokens = Math.min(capacity, tokens + added);
        lastRefillNanos = now;
    }
}
