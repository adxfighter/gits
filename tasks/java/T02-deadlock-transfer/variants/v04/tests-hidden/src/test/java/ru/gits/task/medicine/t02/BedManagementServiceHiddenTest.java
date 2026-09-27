package ru.gits.task.medicine.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BedManagementServiceHiddenTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final BedManagementService service = new BedManagementService();

    @Test
    void oppositeTransfersFinishInTime() {
        List<Ward> wards = hospital(2, 1_000, 400);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 30_000; i++) {
                if (thread % 2 == 0) {
                    service.transfer(wards.get(0), wards.get(1));
                } else {
                    service.transfer(wards.get(1), wards.get(0));
                }
            }
        }), "opposite transfers must not hang");

        assertThat(wards.get(0).occupied() + wards.get(1).occupied()).isEqualTo(800);
    }

    @Test
    void redistributionWithDifferentlyOrderedListsFinishes() {
        List<Ward> wards = hospital(5, 200, 100);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            var random = new Random(thread);
            for (int i = 0; i < 3_000; i++) {
                List<Ward> shuffled = new ArrayList<>(wards);
                Collections.shuffle(shuffled, random);
                if (thread % 3 == 0) {
                    service.redistribute(shuffled.subList(0, 3));
                } else if (thread % 3 == 1) {
                    service.transfer(shuffled.get(0), shuffled.get(1));
                } else {
                    service.totalPatients(shuffled);
                }
            }
        }), "redistribution, transfers and summaries must not hang");

        assertThat(service.totalPatients(wards)).isEqualTo(500);
    }

    @Test
    void summaryIsAlwaysConsistentDuringTransfers() {
        List<Ward> wards = hospital(5, 300, 60);
        var wrongTotals = new AtomicInteger();

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            var random = new Random(100 + thread);
            for (int i = 0; i < 10_000; i++) {
                if (thread == 0) {
                    if (service.totalPatients(wards) != 300) {
                        wrongTotals.incrementAndGet();
                    }
                } else {
                    Ward from = wards.get(random.nextInt(5));
                    Ward to = wards.get(random.nextInt(5));
                    if (from != to) {
                        service.transfer(from, to);
                    }
                }
            }
        }));

        assertThat(wrongTotals.get()).as("summaries that saw a patient in two wards or in none").isZero();
    }

    @Test
    void oppositeDirectWardTransfersFinishInTime() {
        List<Ward> wards = hospital(2, 1_000, 400);
        Ward cardiology = wards.get(0);
        Ward neurology = wards.get(1);

        // other modules call Ward.transferTo directly, bypassing the service
        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(2, thread -> {
            for (int i = 0; i < 30_000; i++) {
                if (thread == 0) {
                    cardiology.transferTo(neurology);
                } else {
                    neurology.transferTo(cardiology);
                }
            }
        }), "opposite direct Ward.transferTo calls must not hang");

        assertThat(cardiology.occupied() + neurology.occupied()).isEqualTo(800);
        assertThat(cardiology.occupied()).isLessThanOrEqualTo(cardiology.beds());
        assertThat(neurology.occupied()).isLessThanOrEqualTo(neurology.beds());
    }

    @Test
    void transferToAFullWardChangesNothing() {
        var surgery = new Ward(1, 2);
        var therapy = new Ward(2, 1);
        surgery.admit(new Patient("MR-1"));
        therapy.admit(new Patient("MR-2"));

        assertThat(service.transfer(surgery, therapy)).isEmpty();
        assertThat(service.transfer(new Ward(3, 1), therapy)).isEmpty();
        assertThat(surgery.occupied()).isEqualTo(1);
        assertThat(therapy.occupied()).isEqualTo(1);
        assertThatThrownBy(() -> therapy.admit(new Patient("MR-3"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void redistributionRespectsCapacityAndInvalidArguments() {
        var small = new Ward(1, 2);
        var large = new Ward(2, 10);
        for (int i = 0; i < 9; i++) {
            large.admit(new Patient("MR-" + i));
        }

        service.redistribute(List.of(large, small));

        assertThat(small.occupied()).isEqualTo(2);
        assertThat(large.occupied()).isEqualTo(7);
        assertThat(service.redistribute(List.of(small))).isZero();
        assertThatThrownBy(() -> service.transfer(small, small)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Wards numbered 1..count; patients spread evenly. */
    private static List<Ward> hospital(int count, int beds, int patientsPerWard) {
        List<Ward> wards = new ArrayList<>();
        int record = 0;
        for (int w = 1; w <= count; w++) {
            var ward = new Ward(w, beds);
            for (int p = 0; p < patientsPerWard; p++) {
                ward.admit(new Patient("MR-" + record++));
            }
            wards.add(ward);
        }
        return wards;
    }

    @FunctionalInterface
    private interface ThreadWork {
        void run(int threadNo) throws Exception;
    }

    /** Worker threads are daemons, so a deadlocked run cannot keep the JVM alive. */
    private static void runConcurrently(int threads, ThreadWork work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
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
