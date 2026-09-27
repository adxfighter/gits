package ru.gits.task.energy.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class FlowDispatcherHiddenTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final FlowDispatcher dispatcher = new FlowDispatcher();

    @Test
    void oppositeShiftsFinishInTime() {
        List<GridNode> grid = grid(2);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 30_000; i++) {
                if (thread % 2 == 0) {
                    dispatcher.shift(grid.get(0), grid.get(1), 1);
                } else {
                    dispatcher.shift(grid.get(1), grid.get(0), 1);
                }
            }
        }), "opposite shifts must not hang");

        assertThat(totalLoad(grid)).isEqualTo(2 * 500_000);
    }

    @Test
    void batchesInAnyOrderFinishTogetherWithSingleShifts() {
        List<GridNode> grid = grid(5);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            var random = new Random(thread);
            for (int i = 0; i < 5_000; i++) {
                if (thread % 2 == 0) {
                    dispatcher.applyBatch(randomBatch(grid, random));
                } else {
                    GridNode from = grid.get(random.nextInt(5));
                    GridNode to = grid.get(random.nextInt(5));
                    if (from != to) {
                        dispatcher.shift(from, to, 1);
                    }
                }
            }
        }), "batches and single shifts must not hang");

        assertThat(totalLoad(grid)).isEqualTo(5 * 500_000);
    }

    @Test
    void nodeRepeatedInABatchInReverseOrderDoesNotHang() {
        List<GridNode> grid = grid(3);
        GridNode a = grid.get(0);
        GridNode b = grid.get(1);
        GridNode c = grid.get(2);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 10_000; i++) {
                if (thread % 2 == 0) {
                    dispatcher.applyBatch(List.of(new Flow(a, b, 1), new Flow(b, c, 1), new Flow(c, a, 1)));
                } else {
                    dispatcher.applyBatch(List.of(new Flow(c, b, 1), new Flow(b, a, 1), new Flow(a, c, 1)));
                }
            }
        }));

        assertThat(List.of(a.load(), b.load(), c.load())).containsOnly(500_000);
    }

    @Test
    void rejectedBatchesUnderLoadKeepTheGridIntact() {
        var small = new GridNode(1, 10, 5);
        var big = new GridNode(2, 1_000_000, 500_000);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 10_000; i++) {
                try {
                    if (thread % 2 == 0) {
                        dispatcher.applyBatch(List.of(new Flow(big, small, 3), new Flow(big, small, 3)));
                    } else {
                        dispatcher.applyBatch(List.of(new Flow(small, big, 3)));
                    }
                } catch (IllegalStateException expected) {
                    // the batch would overload or empty the small node
                }
            }
        }));

        assertThat(small.load() + big.load()).isEqualTo(500_005);
        assertThat(small.load()).isBetween(0, 10);
    }

    @Test
    void validationIsUnchanged() {
        var north = new GridNode(1, 100, 10);
        var south = new GridNode(2, 100, 10);

        assertThatThrownBy(() -> dispatcher.shift(north, south, 11)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> dispatcher.shift(north, north, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.shift(north, south, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(dispatcher.applyBatch(List.of())).isZero();
        assertThat(north.load()).isEqualTo(10);
        assertThat(south.load()).isEqualTo(10);
    }

    private static List<Flow> randomBatch(List<GridNode> grid, Random random) {
        List<Flow> flows = new ArrayList<>();
        for (int f = 0; f < 3; f++) {
            GridNode from = grid.get(random.nextInt(grid.size()));
            GridNode to = grid.get(random.nextInt(grid.size()));
            if (from != to) {
                flows.add(new Flow(from, to, 1));
            }
        }
        return flows;
    }

    private static List<GridNode> grid(int nodes) {
        List<GridNode> grid = new ArrayList<>();
        for (int id = 1; id <= nodes; id++) {
            grid.add(new GridNode(id, 1_000_000, 500_000));
        }
        return grid;
    }

    private static long totalLoad(List<GridNode> grid) {
        return grid.stream().mapToLong(GridNode::load).sum();
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
