package ru.gits.task.logistics.t09;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Per-carrier limits of pickup orders: an order of n places needs n permits.
 */
public final class CarrierOrderLimiter {

    private final long capacity;
    private final long tokensPerSecond;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, CarrierBucket> buckets = new ConcurrentHashMap<>();

    public CarrierOrderLimiter(long capacity, long tokensPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /** Takes all the permits or none of them. */
    public boolean tryAcquire(String carrierId, int permits) {
        checkPermits(permits);
        return bucket(carrierId).tryAcquire(permits);
    }

    /**
     * Nanoseconds after which {@code tryAcquire(carrierId, permits)} succeeds if nobody else takes permits:
     * 0 when it succeeds now, {@link Long#MAX_VALUE} when it never can.
     */
    public long retryAfterNanos(String carrierId, int permits) {
        checkPermits(permits);
        return bucket(carrierId).retryAfterNanos(permits);
    }

    public long available(String carrierId) {
        return bucket(carrierId).available();
    }

    private static void checkPermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }
    }

    private CarrierBucket bucket(String carrierId) {
        Objects.requireNonNull(carrierId, "carrierId");
        return buckets.computeIfAbsent(carrierId, id -> new CarrierBucket(capacity, tokensPerSecond, nanoClock));
    }
}
