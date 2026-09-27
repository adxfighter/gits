package ru.gits.task.energy.t09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TelemetryQuotaHiddenTest {

    private static final int THREADS = 4;

    private final AtomicLong now = new AtomicLong();

    /** Every thread sends the same batches for every device at the same time; returns accepted batches per device. */
    private static AtomicIntegerArray burst(TelemetryQuota quota, int devices, int batchesPerThread, int permits)
            throws Exception {
        AtomicIntegerArray accepted = new AtomicIntegerArray(devices);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> workers = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                workers.add(pool.submit(() -> {
                    start.await();
                    for (int device = 0; device < devices; device++) {
                        for (int batch = 0; batch < batchesPerThread; batch++) {
                            if (quota.tryAcquire("meter-" + device, permits)) {
                                accepted.incrementAndGet(device);
                            }
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> worker : workers) {
                worker.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return accepted;
    }

    @Test
    void refusedBatchConsumesNothing() {
        var quota = new TelemetryQuota(10, 1, now::get);

        assertThat(quota.tryAcquire("meter-1", 7)).isTrue();
        assertThat(quota.tryAcquire("meter-1", 5)).isFalse();
        assertThat(quota.available("meter-1")).isEqualTo(3);
        assertThat(quota.tryAcquire("meter-1", 3)).isTrue();
    }

    @Test
    void batchLargerThanCapacityIsNeverAccepted() {
        var quota = new TelemetryQuota(10, 1, now::get);

        assertThat(quota.tryAcquire("meter-1", 11)).isFalse();
        assertThat(quota.available("meter-1")).isEqualTo(10);

        now.addAndGet(TimeUnit.DAYS.toNanos(1));
        assertThat(quota.tryAcquire("meter-1", 11)).isFalse();
    }

    @Test
    void nonPositivePermitsAreRejected() {
        var quota = new TelemetryQuota(10, 1, now::get);

        assertThatThrownBy(() -> quota.tryAcquire("meter-1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota.tryAcquire("meter-1", -3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(quota.available("meter-1")).isEqualTo(10);
    }

    @Test
    void parallelBatchesOfManyDevicesAreCountedExactly() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            var quota = new TelemetryQuota(10, 1, now::get);

            AtomicIntegerArray accepted = burst(quota, 1_000, 2, 3);

            for (int device = 0; device < accepted.length(); device++) {
                assertThat(accepted.get(device)).as("device %d", device).isEqualTo(3);
                assertThat(quota.available("meter-" + device)).as("device %d", device).isEqualTo(1);
            }
        });
    }

    @Test
    void batchesOfOneBusyDeviceAreAtomic() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int round = 0; round < 10; round++) {
                // the clock yields, so threads interleave inside a request as they would under real load
                var quota = new TelemetryQuota(10_000, 1, () -> {
                    Thread.yield();
                    return now.get();
                });

                AtomicIntegerArray accepted = burst(quota, 1, 1_000, 7);

                assertThat(accepted.get(0)).as("round %d", round).isEqualTo(10_000 / 7);
                assertThat(quota.available("meter-0")).as("round %d", round).isEqualTo(10_000 % 7);
            }
        });
    }

    @Test
    void stalledDeviceDoesNotBlockOtherDevices() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            CountDownLatch stalled = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            // the clock hangs for the thread serving the slow device, i.e. inside its bucket
            var quota = new TelemetryQuota(10, 1, () -> {
                if (Thread.currentThread().getName().equals("slow-device")) {
                    stalled.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return now.get();
            });
            assertThat(quota.tryAcquire("meter-slow", 1)).isTrue();
            assertThat(quota.tryAcquire("meter-fast", 1)).isTrue();

            Thread slow = new Thread(() -> quota.tryAcquire("meter-slow", 2), "slow-device");
            slow.setDaemon(true);
            AtomicBoolean fastAccepted = new AtomicBoolean();
            Thread fast = new Thread(() -> fastAccepted.set(quota.tryAcquire("meter-fast", 2)), "fast-device");
            fast.setDaemon(true);
            try {
                slow.start();
                assertThat(stalled.await(5, TimeUnit.SECONDS)).as("slow device reads the clock").isTrue();
                fast.start();
                fast.join(2_000);
                assertThat(fast.isAlive()).as("another device waits for the stalled one").isFalse();
                assertThat(fastAccepted).isTrue();
            } finally {
                release.countDown();
            }
            slow.join(5_000);
        });
    }

    @Test
    void refillAfterARefusalIsUsable() {
        var quota = new TelemetryQuota(8, 2, now::get);
        assertThat(quota.tryAcquire("meter-1", 6)).isTrue();
        assertThat(quota.tryAcquire("meter-1", 4)).isFalse();

        now.addAndGet(1_000_000_000L);

        assertThat(quota.tryAcquire("meter-1", 4)).isTrue();
        assertThat(quota.available("meter-1")).isZero();
    }
}
