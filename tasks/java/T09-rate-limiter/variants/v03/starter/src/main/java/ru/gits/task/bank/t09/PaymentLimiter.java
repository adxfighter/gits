package ru.gits.task.bank.t09;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Token bucket limiting payment operations of a merchant. Called concurrently by authorization threads.
 *
 * <p>One token is {@value #UNITS_PER_TOKEN} units; every elapsed nanosecond adds {@code tokensPerSecond}
 * units, so no fraction is lost.
 */
public final class PaymentLimiter {

    private static final long UNITS_PER_TOKEN = 1_000_000_000L;

    private final long maxUnits;
    private final long tokensPerSecond;
    private final LongSupplier nanoClock;

    private volatile long units;
    private volatile long lastRefillNanos;

    public PaymentLimiter(long capacity, long tokensPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0 || tokensPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and rate must be positive");
        }
        this.maxUnits = Math.multiplyExact(capacity, UNITS_PER_TOKEN);
        this.tokensPerSecond = tokensPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.units = maxUnits;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Takes one token if there is one; never waits. */
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
        if (elapsed <= 0) {
            return;
        }
        long missing = maxUnits - units;
        if (elapsed >= missing / tokensPerSecond + 1) {
            units = maxUnits;
        } else {
            units = units + elapsed * tokensPerSecond;
        }
        lastRefillNanos = now;
    }
}
