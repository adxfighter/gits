package ru.gits.task.logistics.t02;

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

import org.junit.jupiter.api.Test;

class StockMovementServiceHiddenTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final String SKU = "PALLET";

    private final StockMovementService service = new StockMovementService();

    @Test
    void circularMovesBetweenThreeWarehousesFinish() {
        List<Warehouse> ring = warehouses("MSK", "KZN", "EKB");

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            Warehouse from = ring.get(thread % 3);
            Warehouse to = ring.get((thread + 1) % 3);
            for (int i = 0; i < 30_000; i++) {
                service.move(from, to, SKU, 1);
            }
        }), "circular moves must not hang");

        assertThat(total(ring)).isEqualTo(3 * 1_000_000);
    }

    @Test
    void oppositeMovesFinish() {
        List<Warehouse> pair = warehouses("MSK", "KZN");

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 30_000; i++) {
                if (thread % 2 == 0) {
                    service.move(pair.get(0), pair.get(1), SKU, 1);
                } else {
                    service.move(pair.get(1), pair.get(0), SKU, 1);
                }
            }
        }));

        assertThat(pair.get(0).quantity(SKU)).isEqualTo(1_000_000);
        assertThat(pair.get(1).quantity(SKU)).isEqualTo(1_000_000);
    }

    @Test
    void rebalancingInParallelWithMovesFinishesAndKeepsTheTotal() {
        List<Warehouse> ring = warehouses("MSK", "KZN", "EKB");

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            Warehouse a = ring.get(thread % 3);
            Warehouse b = ring.get((thread + 2) % 3);
            for (int i = 0; i < 20_000; i++) {
                if (thread < 3) {
                    service.move(a, b, SKU, 2);
                } else {
                    service.rebalance(a, b, SKU);
                }
            }
        }));

        assertThat(total(ring)).isEqualTo(3 * 1_000_000);
        assertThat(ring).allMatch(warehouse -> warehouse.quantity(SKU) >= 0);
    }

    @Test
    void failedMoveChangesNothing() {
        var moscow = new Warehouse("MSK");
        var kazan = new Warehouse("KZN");
        moscow.receive(SKU, 3);

        assertThatThrownBy(() -> service.move(moscow, kazan, SKU, 4)).isInstanceOf(IllegalStateException.class);
        assertThat(moscow.quantity(SKU)).isEqualTo(3);
        assertThat(kazan.quantity(SKU)).isZero();
    }

    @Test
    void rebalanceOfEvenStockMovesNothing() {
        var moscow = new Warehouse("MSK");
        var kazan = new Warehouse("KZN");
        moscow.receive(SKU, 5);
        kazan.receive(SKU, 4);

        assertThat(service.rebalance(moscow, kazan, SKU)).isZero();
        assertThat(moscow.quantity(SKU)).isEqualTo(5);
    }

    @Test
    void rejectsInvalidArguments() {
        var moscow = new Warehouse("MSK");
        var kazan = new Warehouse("KZN");

        assertThatThrownBy(() -> service.move(moscow, moscow, SKU, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.move(moscow, new Warehouse("MSK"), SKU, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.move(moscow, kazan, SKU, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rebalance(kazan, kazan, SKU)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Warehouse> warehouses(String... codes) {
        List<Warehouse> result = new ArrayList<>();
        for (String code : codes) {
            var warehouse = new Warehouse(code);
            warehouse.receive(SKU, 1_000_000);
            result.add(warehouse);
        }
        return result;
    }

    private static long total(List<Warehouse> warehouses) {
        return warehouses.stream().mapToLong(w -> w.quantity(SKU)).sum();
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
