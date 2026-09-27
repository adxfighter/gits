package ru.gits.task.energy.t09;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Telemetry quotas of devices: a batch of n records needs n permits.
 */
public final class TelemetryQuota {

    private final long capacity;
    private final long tokensPerSecond;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, DeviceBucket> buckets = new ConcurrentHashMap<>();

    public TelemetryQuota(long capacity, long tokensPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * Accepts a batch when the device has all the permits; a refused batch consumes nothing.
     *
     * @throws IllegalArgumentException when permits is less than 1
     */
    public boolean tryAcquire(String deviceId, int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }
        return bucket(deviceId).tryAcquire(permits);
    }

    /** Whole permits the device has now. */
    public long available(String deviceId) {
        return bucket(deviceId).available();
    }

    private DeviceBucket bucket(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        return buckets.computeIfAbsent(deviceId, id -> new DeviceBucket(capacity, tokensPerSecond, nanoClock));
    }
}
