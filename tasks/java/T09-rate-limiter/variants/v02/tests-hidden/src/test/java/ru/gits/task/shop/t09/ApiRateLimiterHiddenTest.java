package ru.gits.task.shop.t09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ApiRateLimiterHiddenTest {

    private static final int THREADS = 4;
    private static final int CAPACITY = 3;

    private final AtomicLong now = new AtomicLong();

    /** All threads request the same new clients at the same time; returns successes per client. */
    private AtomicIntegerArray burst(ApiRateLimiter limiter, String prefix, int clients) throws Exception {
        AtomicIntegerArray granted = new AtomicIntegerArray(clients);
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
                    for (int client = 0; client < clients; client++) {
                        for (int attempt = 0; attempt < CAPACITY; attempt++) {
                            if (limiter.tryAcquire(prefix + client)) {
                                granted.incrementAndGet(client);
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
    void newClientsNeverExceedTheirLimitUnderConcurrency() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new ApiRateLimiter(CAPACITY, 1, now::get);
            for (int round = 0; round < 5; round++) {
                AtomicIntegerArray granted = burst(limiter, "r" + round + "-client-", 3_000);
                for (int client = 0; client < granted.length(); client++) {
                    assertThat(granted.get(client)).as("round %d, client %d", round, client).isEqualTo(CAPACITY);
                }
            }
        });
    }

    @Test
    void existingClientsKeepTheirBucketsWhileNewOnesArrive() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new ApiRateLimiter(CAPACITY, 1, now::get);
            for (int i = 0; i < CAPACITY; i++) {
                assertThat(limiter.tryAcquire("vip")).isTrue();
            }

            burst(limiter, "new-", 5_000);

            assertThat(limiter.tryAcquire("vip")).as("exhausted bucket must not be recreated").isFalse();
        });
    }

    @Test
    void concurrentlyCreatedBucketsRefillOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new ApiRateLimiter(CAPACITY, 1, now::get);
            burst(limiter, "client-", 2_000);

            now.addAndGet(1_000_000_000L);
            AtomicIntegerArray afterRefill = burst(limiter, "client-", 2_000);

            for (int client = 0; client < afterRefill.length(); client++) {
                assertThat(afterRefill.get(client)).as("client %d after one second", client).isEqualTo(1);
            }
        });
    }

    @Test
    void oneBusyClientUnderConcurrency() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new ApiRateLimiter(CAPACITY, 1, now::get);
            AtomicIntegerArray granted = burst(limiter, "solo", 1);

            assertThat(granted.get(0)).isEqualTo(CAPACITY);
        });
    }

    @Test
    void sequentialLimitsStillHold() {
        var limiter = new ApiRateLimiter(2, 2, now::get);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        now.addAndGet(500_000_000L);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }
}
