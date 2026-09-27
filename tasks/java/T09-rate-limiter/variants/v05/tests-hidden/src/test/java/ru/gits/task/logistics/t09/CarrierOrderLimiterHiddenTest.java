package ru.gits.task.logistics.t09;

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
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class CarrierOrderLimiterHiddenTest {

    private static final int THREADS = 8;

    private final AtomicLong now = new AtomicLong();

    /** Every thread sends orders to every carrier at the same time; returns granted permits per carrier. */
    private static AtomicLongArray burst(CarrierOrderLimiter limiter, int carriers, int ordersPerThread, int places)
            throws Exception {
        AtomicLongArray granted = new AtomicLongArray(carriers);
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
                    for (int carrier = 0; carrier < carriers; carrier++) {
                        for (int order = 0; order < ordersPerThread; order++) {
                            if (limiter.tryAcquire("carrier-" + carrier, places)) {
                                granted.addAndGet(carrier, places);
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
        return granted;
    }

    @Test
    void idleNightDoesNotOverfillTheBucket() {
        var limiter = new CarrierOrderLimiter(50, 5, now::get);
        assertThat(limiter.tryAcquire("cdek", 50)).isTrue();

        now.addAndGet(TimeUnit.HOURS.toNanos(10));

        assertThat(limiter.available("cdek")).isEqualTo(50);
        assertThat(limiter.tryAcquire("cdek", 50)).isTrue();
        assertThat(limiter.tryAcquire("cdek", 1)).isFalse();
    }

    @Test
    void newCarrierAfterIdleStartsAtCapacity() {
        var limiter = new CarrierOrderLimiter(30, 1, now::get);
        limiter.available("pek");

        now.addAndGet(TimeUnit.DAYS.toNanos(3));

        assertThat(limiter.available("pek")).isEqualTo(30);
    }

    @Test
    void waitingRetryAfterIsEnough() {
        var limiter = new CarrierOrderLimiter(10, 3, now::get);   // a token every 333 333 333.3 ns
        assertThat(limiter.tryAcquire("cdek", 10)).isTrue();

        for (int places = 1; places <= 10; places++) {
            long wait = limiter.retryAfterNanos("cdek", places);
            now.addAndGet(wait);
            assertThat(limiter.tryAcquire("cdek", places)).as("%d places after %d ns", places, wait).isTrue();
        }
    }

    @Test
    void retryAfterIsTheMinimalWait() {
        var limiter = new CarrierOrderLimiter(10, 3, now::get);
        assertThat(limiter.tryAcquire("cdek", 10)).isTrue();

        long wait = limiter.retryAfterNanos("cdek", 1);
        assertThat(wait).isEqualTo(333_333_334L);
        now.addAndGet(wait - 1);
        assertThat(limiter.tryAcquire("cdek", 1)).isFalse();
    }

    @Test
    void orderLargerThanCapacityNeverFits() {
        var limiter = new CarrierOrderLimiter(10, 3, now::get);

        assertThat(limiter.retryAfterNanos("cdek", 11)).isEqualTo(Long.MAX_VALUE);
        now.addAndGet(TimeUnit.DAYS.toNanos(1));
        assertThat(limiter.tryAcquire("cdek", 11)).isFalse();
        assertThat(limiter.retryAfterNanos("cdek", 11)).isEqualTo(Long.MAX_VALUE);
        assertThat(limiter.available("cdek")).isEqualTo(10);
    }

    @Test
    void invalidPermitsAreRejected() {
        var limiter = new CarrierOrderLimiter(10, 3, now::get);

        assertThatThrownBy(() -> limiter.tryAcquire("cdek", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.retryAfterNanos("cdek", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void morningBurstAfterIdleIsLimitedExactly() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            var limiter = new CarrierOrderLimiter(40, 2, now::get);
            for (int carrier = 0; carrier < 200; carrier++) {
                assertThat(limiter.tryAcquire("carrier-" + carrier, 40)).isTrue();
            }
            now.addAndGet(TimeUnit.HOURS.toNanos(8));

            AtomicLongArray granted = burst(limiter, 200, 20, 3);

            for (int carrier = 0; carrier < granted.length(); carrier++) {
                assertThat(granted.get(carrier)).as("carrier %d", carrier).isEqualTo(39);
                assertThat(limiter.available("carrier-" + carrier)).as("carrier %d", carrier).isEqualTo(1);
            }
        });
    }
}
