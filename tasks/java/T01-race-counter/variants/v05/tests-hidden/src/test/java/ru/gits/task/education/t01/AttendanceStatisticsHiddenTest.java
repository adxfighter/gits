package ru.gits.task.education.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AttendanceStatisticsHiddenTest {

    private static final int SERVERS = 8;
    private static final List<Integer> LESSON = Collections.nCopies(10, 60);

    @Test
    void singleVisitsAndLessonsFromManyServersAreNotLost() throws Exception {
        for (int round = 0; round < 3; round++) {
            var statistics = new AttendanceStatistics();

            runConcurrently(SERVERS, server -> {
                for (int i = 0; i < 20_000; i++) {
                    if (server % 2 == 0) {
                        statistics.record(60);
                    } else {
                        statistics.recordAll(LESSON);
                    }
                }
            });

            long visits = SERVERS / 2 * 20_000L + SERVERS / 2 * 20_000L * LESSON.size();
            assertThat(statistics.summary()).as("round %d", round)
                    .isEqualTo(new AttendanceSummary(visits, visits * 60, 60));
        }
    }

    @Test
    void lessonsRecordedOnlyInBatchesAreNotLost() throws Exception {
        var statistics = new AttendanceStatistics();

        runConcurrently(SERVERS, server -> {
            for (int i = 0; i < 20_000; i++) {
                statistics.recordAll(List.of(10, 20));
            }
        });

        assertThat(statistics.summary()).isEqualTo(new AttendanceSummary(SERVERS * 40_000L, SERVERS * 600_000L, 20));
    }

    @Test
    void everySummaryIsConsistentDuringWrites() throws Exception {
        var statistics = new AttendanceStatistics();
        var writing = new AtomicBoolean(true);
        var inconsistent = new AtomicReference<AttendanceSummary>();
        ExecutorService methodologist = Executors.newSingleThreadExecutor();
        Future<?> reader = methodologist.submit(() -> {
            while (writing.get() && inconsistent.get() == null) {
                AttendanceSummary summary = statistics.summary();
                // every visit lasts 60 minutes, so a consistent summary has total == 60 * visits
                if (summary.totalMinutes() != summary.visits() * 60) {
                    inconsistent.set(summary);
                }
            }
        });
        try {
            for (int round = 0; round < 5 && inconsistent.get() == null; round++) {
                runConcurrently(SERVERS, server -> {
                    for (int i = 0; i < 10_000; i++) {
                        if (server % 2 == 0) {
                            statistics.record(60);
                        } else {
                            statistics.recordAll(LESSON);
                        }
                    }
                });
            }
        } finally {
            writing.set(false);
            reader.get(10, TimeUnit.SECONDS);
            methodologist.shutdownNow();
        }

        assertThat(inconsistent.get()).as("summary mixing different moments").isNull();
    }

    @Test
    void maximumSurvivesConcurrentLessons() throws Exception {
        var statistics = new AttendanceStatistics();

        runConcurrently(SERVERS, server -> {
            for (int i = 0; i < 5_000; i++) {
                statistics.recordAll(List.of(1 + (server * 5_000 + i) % AttendanceStatistics.MAX_VISIT_MINUTES));
            }
        });

        assertThat(statistics.summary().maxMinutes()).isEqualTo(AttendanceStatistics.MAX_VISIT_MINUTES);
        assertThat(statistics.summary().visits()).isEqualTo(SERVERS * 5_000L);
    }

    @Test
    void invalidBatchIsRejectedAsAWhole() {
        var statistics = new AttendanceStatistics();
        statistics.record(30);

        assertThatThrownBy(() -> statistics.recordAll(List.of(40, AttendanceStatistics.MAX_VISIT_MINUTES + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> statistics.recordAll(null)).isInstanceOf(NullPointerException.class);

        assertThat(statistics.summary()).isEqualTo(new AttendanceSummary(1, 30, 30));
    }

    @FunctionalInterface
    private interface ServerWork {
        void run(int serverNo) throws Exception;
    }

    private static void runConcurrently(int threads, ServerWork work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int serverNo = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    work.run(serverNo);
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
