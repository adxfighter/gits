package ru.gits.task.bank.t09;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PaymentLimiterHiddenTest {

    private static final int THREADS = 8;

    private final AtomicLong now = new AtomicLong();

    /** THREADS threads start together and call tryAcquire; returns the number of granted tokens. */
    private static int burst(PaymentLimiter limiter, int attemptsPerThread, Runnable eachAttempt) throws Exception {
        AtomicInteger granted = new AtomicInteger();
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
                    for (int i = 0; i < attemptsPerThread; i++) {
                        eachAttempt.run();
                        if (limiter.tryAcquire()) {
                            granted.incrementAndGet();
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
        return granted.get();
    }

    private static void drain(PaymentLimiter limiter) {
        while (limiter.tryAcquire()) {
            // take everything
        }
    }

    @Test
    void exactlyCapacityIsGrantedUnderContention() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int round = 0; round < 10; round++) {
                var limiter = new PaymentLimiter(20_000, 1, now::get);
                assertThat(burst(limiter, 5_000, () -> { })).as("round %d", round).isEqualTo(20_000);
                assertThat(limiter.available()).isZero();
            }
        });
    }

    @Test
    void refillDuringContentionIsCountedExactlyOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int round = 0; round < 5; round++) {
                var limiter = new PaymentLimiter(1_000_000, 1_000, now::get);
                drain(limiter);
                long startNanos = now.get();

                // every attempt moves the clock by 10 microseconds: 0.01 token
                int granted = burst(limiter, 20_000, () -> now.addAndGet(10_000L));

                long refilledTokens = (now.get() - startNanos) * 1_000 / 1_000_000_000L;
                assertThat(granted + limiter.available()).as("round %d", round).isEqualTo(refilledTokens);
            }
        });
    }

    @Test
    void nothingIsGrantedFromAnEmptyBucket() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new PaymentLimiter(5_000, 1, now::get);
            drain(limiter);

            assertThat(burst(limiter, 2_000, () -> { })).isZero();
        });
    }

    @Test
    void mixedTakersAndReadersKeepTheCount() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            var limiter = new PaymentLimiter(30_000, 1, now::get);
            AtomicInteger reads = new AtomicInteger();

            int granted = burst(limiter, 6_000, () -> {
                if (reads.incrementAndGet() % 7 == 0) {
                    limiter.available();
                }
            });

            assertThat(granted).isEqualTo(30_000);
        });
    }

    @Test
    void singleThreadBehaviourIsUnchanged() {
        var limiter = new PaymentLimiter(2, 2, now::get);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        now.addAndGet(250_000_000L);
        assertThat(limiter.tryAcquire()).isFalse();
        now.addAndGet(250_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();

        now.addAndGet(TimeUnit.DAYS.toNanos(365));
        assertThat(limiter.available()).isEqualTo(2);
    }
}
