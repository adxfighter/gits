package ru.gits.task.telecom.t09;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Token bucket limiting SMS sent through the gateway by one customer. Not thread-safe.
 *
 * <p>Tokens are kept in exact units: one token is {@value #UNITS_PER_TOKEN} units, and every elapsed
 * nanosecond adds {@code tokensPerMinute} units. No fraction of a token is ever lost.
 */
public final class SmsRateLimiter {

    /** Nanoseconds in a minute: at N tokens per minute a nanosecond brings N units. */
    private static final long UNITS_PER_TOKEN = 60_000_000_000L;

    private final long capacity;
    private final long tokensPerMinute;
    private final LongSupplier nanoClock;
    private final long maxUnits;

    private long units;
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
        this.maxUnits = Math.multiplyExact(capacity, UNITS_PER_TOKEN);
        this.units = maxUnits;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Takes one token if there is one. */
    public boolean tryAcquire() {
        refill();
        if (units >= UNITS_PER_TOKEN) {
            units -= UNITS_PER_TOKEN;
            return true;
        }
        return false;
    }

    /** Whole tokens available now. */
    public long available() {
        refill();
        return units / UNITS_PER_TOKEN;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        lastRefillNanos = now;
        long missing = maxUnits - units;
        // Compare before multiplying: after a long idle period elapsed * rate would overflow.
        if (elapsed >= missing / tokensPerMinute + 1) {
            units = maxUnits;
        } else {
            units = Math.min(maxUnits, units + elapsed * tokensPerMinute);
        }
    }
}
