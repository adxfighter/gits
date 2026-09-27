package ru.gits.task.logistics.t09;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Token bucket of one carrier. One token is {@value #UNITS_PER_TOKEN} units; every elapsed nanosecond
 * adds {@code tokensPerSecond} units, so no fraction is lost.
 */
public final class CarrierBucket {

    private static final long UNITS_PER_TOKEN = 1_000_000_000L;

    private final long capacity;
    private final long tokensPerSecond;
    private final LongSupplier nanoClock;

    private long units;
    private long lastRefillNanos;

    /** Creates a full bucket. */
    public CarrierBucket(long capacity, long tokensPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0 || tokensPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and rate must be positive");
        }
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.units = Math.multiplyExact(capacity, UNITS_PER_TOKEN);
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Takes all the permits or none of them. */
    public synchronized boolean tryAcquire(int permits) {
        refill();
        long needed = permits * UNITS_PER_TOKEN;
        if (units >= needed) {
            units -= needed;
            return true;
        }
        return false;
    }

    /** Nanoseconds until the permits are available, if nobody else takes tokens. */
    public synchronized long retryAfterNanos(int permits) {
        refill();
        long missing = permits * UNITS_PER_TOKEN - units;
        if (missing <= 0) {
            return 0;
        }
        return missing / tokensPerSecond;
    }

    public synchronized long available() {
        refill();
        return units / UNITS_PER_TOKEN;
    }

    public long capacity() {
        return capacity;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            units += elapsed * tokensPerSecond;
            lastRefillNanos = now;
        }
    }
}
