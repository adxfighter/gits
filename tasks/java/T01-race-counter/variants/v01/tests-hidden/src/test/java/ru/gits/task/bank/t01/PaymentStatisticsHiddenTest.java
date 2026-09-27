package ru.gits.task.bank.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PaymentStatisticsHiddenTest {

    private static final ThreadFactory DAEMON_THREADS = task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        return thread;
    };

    private static final int THREADS = 4;
    private static final int PAYMENTS_PER_THREAD = 50_000;

    private static final Payment VALID = new Payment("p", 100, "40817810000000000001");
    private static final Payment INVALID = new Payment("bad", -5, "40817810000000000001");

    @Test
    void everyPaymentIsCountedWhenThreadsRecordAtTheSameTime() throws Exception {
        var statistics = new PaymentStatistics();
        for (int round = 0; round < 5; round++) {
            statistics.reset();

            runConcurrently(THREADS, () -> {
                for (int i = 0; i < PAYMENTS_PER_THREAD; i++) {
                    statistics.record(VALID);
                }
            });

            assertThat(statistics.processedCount()).as("round %d", round).isEqualTo(THREADS * PAYMENTS_PER_THREAD);
        }
    }

    @Test
    void invalidPaymentsAreNotCountedUnderConcurrency() throws Exception {
        var statistics = new PaymentStatistics();
        var rejected = new AtomicInteger();

        runConcurrently(THREADS, () -> {
            for (int i = 0; i < PAYMENTS_PER_THREAD; i++) {
                try {
                    statistics.record(i % 3 == 0 ? INVALID : VALID);
                } catch (IllegalArgumentException e) {
                    rejected.incrementAndGet();
                }
            }
        });

        assertThat(statistics.processedCount() + rejected.get()).isEqualTo(THREADS * PAYMENTS_PER_THREAD);
        assertThat(rejected.get()).isEqualTo(THREADS * ((PAYMENTS_PER_THREAD + 2) / 3));
    }

    @Test
    void countIsVisibleToTheMonitoringThread() throws Exception {
        var statistics = new PaymentStatistics();

        runConcurrently(THREADS, () -> {
            for (int i = 0; i < 10_000; i++) {
                statistics.record(VALID);
            }
        });

        ExecutorService monitoring = Executors.newSingleThreadExecutor(DAEMON_THREADS);
        try {
            assertThat(monitoring.submit(statistics::processedCount).get(5, TimeUnit.SECONDS)).isEqualTo(THREADS * 10_000);
        } finally {
            monitoring.shutdownNow();
        }
    }

    @Test
    void resetStartsANewPeriod() {
        var statistics = new PaymentStatistics();
        statistics.record(VALID);
        statistics.record(VALID);

        statistics.reset();
        statistics.record(VALID);

        assertThat(statistics.processedCount()).isEqualTo(1);
    }

    @Test
    void nullPaymentIsRejected() {
        var statistics = new PaymentStatistics();

        assertThatThrownBy(() -> statistics.record(null)).isInstanceOf(NullPointerException.class);
        assertThat(statistics.processedCount()).isZero();
    }

    private static void runConcurrently(int threads, Runnable work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads, DAEMON_THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    work.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
