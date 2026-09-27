package ru.gits.task.shop.t01;

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

class StockItemHiddenTest {

    private static final ThreadFactory DAEMON_THREADS = task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        return thread;
    };

    private static final int BUYERS = 8;

    @Test
    void neverSellsMoreThanTheStockDuringASale() throws Exception {
        for (int round = 0; round < 5; round++) {
            var item = new StockItem("SKU-1", 20_000);
            var reserved = new AtomicInteger();

            runConcurrently(BUYERS, () -> {
                for (int i = 0; i < 5_000; i++) {
                    if (item.reserve("o", 1).isPresent()) {
                        reserved.incrementAndGet();
                    }
                }
            });

            // 8 x 5000 = 40000 attempts for 20000 items: exactly the stock is sold, nothing more
            assertThat(reserved.get()).as("round %d", round).isEqualTo(20_000);
            assertThat(item.available()).as("round %d", round).isZero();
        }
    }

    @Test
    void stockIsExactWhenEveryAttemptSucceeds() throws Exception {
        var item = new StockItem("SKU-1", 200_000);

        runConcurrently(BUYERS, () -> {
            for (int i = 0; i < 10_000; i++) {
                item.reserve("o", 2);
            }
        });

        assertThat(item.available()).isEqualTo(200_000 - BUYERS * 10_000 * 2);
    }

    @Test
    void concurrentReservationsAndCancellationsKeepTheStockConsistent() throws Exception {
        var item = new StockItem("SKU-1", 1_000);
        var negativeSeen = new AtomicInteger();

        runConcurrently(BUYERS, () -> {
            for (int i = 0; i < 20_000; i++) {
                item.reserve("o", 3).ifPresent(item::release);
                if (item.available() < 0) {
                    negativeSeen.incrementAndGet();
                }
            }
        });

        assertThat(negativeSeen.get()).isZero();
        assertThat(item.available()).isEqualTo(1_000);
    }

    @Test
    void refusesWhenStockIsInsufficient() {
        var item = new StockItem("SKU-1", 2);

        assertThat(item.reserve("o-1", 3)).isEmpty();
        assertThat(item.available()).isEqualTo(2);
        assertThat(item.reserve("o-2", 2)).get().extracting(Reservation::quantity).isEqualTo(2);
        assertThat(item.available()).isZero();
    }

    @Test
    void validatesArguments() {
        var item = new StockItem("SKU-1", 5);

        assertThatThrownBy(() -> item.reserve("o", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.release(new Reservation("o", "SKU-2", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockItem("SKU-1", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item.available()).isEqualTo(5);
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
