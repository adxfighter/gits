package ru.gits.task.logistics.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class OrderPipelineVisibleTest {

    @Test
    void singleConsumerHandlesAllOrdersAndStops() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Set<String> handled = ConcurrentHashMap.newKeySet();
            var pipeline = new OrderPipeline(1, order -> handled.add(order.id()));

            for (int i = 0; i < 100; i++) {
                pipeline.submit(new Order("o-" + i, "SKU-1", 1));
            }
            pipeline.shutdown();

            assertThat(pipeline.awaitTermination(Duration.ofSeconds(5))).isTrue();
            assertThat(handled).hasSize(100);
        });
    }

    @Test
    void ordersAreRejectedAfterShutdown() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var pipeline = new OrderPipeline(1, order -> { });
            pipeline.shutdown();

            assertThatThrownBy(() -> pipeline.submit(new Order("o-1", "SKU-1", 1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(pipeline.awaitTermination(Duration.ofSeconds(5))).isTrue();
        });
    }
}
