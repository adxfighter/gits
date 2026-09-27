package ru.gits.task.logistics.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class OrderPipelineHiddenTest {

    private static Map<String, AtomicInteger> runPipeline(int consumers, int orders) throws InterruptedException {
        Map<String, AtomicInteger> handled = new ConcurrentHashMap<>();
        var pipeline = new OrderPipeline(consumers,
                order -> handled.computeIfAbsent(order.id(), id -> new AtomicInteger()).incrementAndGet());

        for (int i = 0; i < orders; i++) {
            pipeline.submit(new Order("o-" + i, "SKU-" + i % 7, 1 + i % 3));
        }
        pipeline.shutdown();

        assertThat(pipeline.awaitTermination(Duration.ofSeconds(3)))
                .as("pipeline with %d consumers stops", consumers)
                .isTrue();
        return handled;
    }

    @Test
    void twoConsumersStop() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Map<String, AtomicInteger> handled = runPipeline(2, 1_000);

            assertThat(handled).hasSize(1_000);
            assertThat(handled.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        });
    }

    @Test
    void fourConsumersStop() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Map<String, AtomicInteger> handled = runPipeline(4, 2_000);

            assertThat(handled).hasSize(2_000);
            assertThat(handled.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        });
    }

    @Test
    void idlePipelineWithSeveralConsumersStops() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var pipeline = new OrderPipeline(3, order -> { });
            pipeline.shutdown();

            assertThat(pipeline.awaitTermination(Duration.ofSeconds(3))).isTrue();
        });
    }

    @Test
    void repeatedShutdownIsHarmless() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            AtomicInteger handled = new AtomicInteger();
            var pipeline = new OrderPipeline(2, order -> handled.incrementAndGet());
            pipeline.submit(new Order("o-1", "SKU-1", 1));

            pipeline.shutdown();
            pipeline.shutdown();

            assertThat(pipeline.awaitTermination(Duration.ofSeconds(3))).isTrue();
            assertThat(handled.get()).isEqualTo(1);
        });
    }

    @Test
    void failingOrderDoesNotStopConsumers() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            AtomicInteger handled = new AtomicInteger();
            var pipeline = new OrderPipeline(2, order -> {
                if (order.quantity() == 0) {
                    throw new IllegalArgumentException("empty order " + order.id());
                }
                handled.incrementAndGet();
            });

            for (int i = 0; i < 200; i++) {
                pipeline.submit(new Order("o-" + i, "SKU-1", i % 10));
            }
            pipeline.shutdown();

            assertThat(pipeline.awaitTermination(Duration.ofSeconds(3))).isTrue();
            assertThat(handled.get()).isEqualTo(180);
        });
    }

    @Test
    void timeoutIsReportedWhileOrdersAreStillBeingHandled() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var release = new java.util.concurrent.CountDownLatch(1);
            var pipeline = new OrderPipeline(2, order -> {
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            pipeline.submit(new Order("o-1", "SKU-1", 1));
            pipeline.shutdown();

            assertThat(pipeline.awaitTermination(Duration.ofMillis(100))).isFalse();
            release.countDown();
            assertThat(pipeline.awaitTermination(Duration.ofSeconds(3))).isTrue();
        });
    }
}
