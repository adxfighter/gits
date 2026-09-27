package ru.gits.task.energy.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ConsumptionAggregatorHiddenTest {

    private static final int CONCENTRATORS = 16;
    private static final int METERS = 32;
    private static final int READINGS_PER_THREAD = 20_000;

    @Test
    void noConsumptionIsLostWhenMetersShareThreads() throws Exception {
        for (int round = 0; round < 3; round++) {
            var aggregator = new ConsumptionAggregator();

            runConcurrently(CONCENTRATORS, thread -> {
                for (int i = 0; i < READINGS_PER_THREAD; i++) {
                    aggregator.add(new MeterReading("M-" + (i % METERS), 10, i));
                }
            });

            long perMeter = (long) CONCENTRATORS * READINGS_PER_THREAD / METERS * 10;
            assertThat(aggregator.meterCount()).as("round %d", round).isEqualTo(METERS);
            for (int m = 0; m < METERS; m++) {
                assertThat(aggregator.totalFor("M-" + m)).as("round %d, meter M-%d", round, m).isEqualTo(perMeter);
            }
            assertThat(aggregator.total()).isEqualTo((long) CONCENTRATORS * READINGS_PER_THREAD * 10);
        }
    }

    @Test
    void everyNewMeterAppearsInTheSummary() throws Exception {
        var aggregator = new ConsumptionAggregator();

        // each thread registers its own 2000 meters: lost insertions show up as missing meters
        runConcurrently(CONCENTRATORS, thread -> {
            for (int i = 0; i < 2_000; i++) {
                aggregator.add(new MeterReading("T" + thread + "-M" + i, 1, 1));
            }
        });

        assertThat(aggregator.meterCount()).isEqualTo(CONCENTRATORS * 2_000);
        assertThat(aggregator.total()).isEqualTo(CONCENTRATORS * 2_000L);
    }

    @Test
    void summaryCanBeReadWhileReadingsArrive() throws Exception {
        var aggregator = new ConsumptionAggregator();
        var writing = new AtomicBoolean(true);
        var failure = new AtomicReference<Throwable>();
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        Future<?> reader = dispatcher.submit(() -> {
            while (writing.get()) {
                try {
                    Map<String, Long> snapshot = aggregator.snapshot();
                    aggregator.total();
                    snapshot.values().forEach(value -> {
                        if (value < 0) {
                            throw new IllegalStateException("negative total");
                        }
                    });
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                    return;
                }
            }
        });
        try {
            runConcurrently(CONCENTRATORS, thread -> {
                for (int i = 0; i < READINGS_PER_THREAD; i++) {
                    aggregator.add(new MeterReading("T" + thread + "-M" + (i % 500), 5, i));
                }
            });
        } finally {
            writing.set(false);
            reader.get(10, TimeUnit.SECONDS);
            dispatcher.shutdownNow();
        }

        assertThat(failure.get()).as("reading the summary during writes").isNull();
        assertThat(aggregator.total()).isEqualTo((long) CONCENTRATORS * READINGS_PER_THREAD * 5);
    }

    @Test
    void snapshotIsADetachedCopy() {
        var aggregator = new ConsumptionAggregator();
        aggregator.add(new MeterReading("M-1", 100, 1));

        Map<String, Long> snapshot = aggregator.snapshot();
        aggregator.add(new MeterReading("M-1", 100, 2));

        assertThat(snapshot).containsEntry("M-1", 100L);
        assertThatThrownBy(() -> snapshot.put("M-2", 1L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationIsUnchanged() {
        var aggregator = new ConsumptionAggregator();

        assertThatThrownBy(() -> aggregator.add(
                new MeterReading("M-1", ConsumptionAggregator.MAX_INTERVAL_WATT_HOURS + 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        aggregator.add(new MeterReading("M-1", ConsumptionAggregator.MAX_INTERVAL_WATT_HOURS, 1));
        aggregator.add(new MeterReading("M-1", 0, 2));

        assertThat(aggregator.totalFor("M-1")).isEqualTo(ConsumptionAggregator.MAX_INTERVAL_WATT_HOURS);
        assertThat(aggregator.totalFor("unknown")).isZero();
    }

    @FunctionalInterface
    private interface ThreadWork {
        void run(int threadNo) throws Exception;
    }

    private static void runConcurrently(int threads, ThreadWork work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int threadNo = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    work.run(threadNo);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
