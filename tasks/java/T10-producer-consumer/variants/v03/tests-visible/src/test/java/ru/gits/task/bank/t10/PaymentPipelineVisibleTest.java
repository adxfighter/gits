package ru.gits.task.bank.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class PaymentPipelineVisibleTest {

    @Test
    void paymentsAreProcessed() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Set<String> processed = ConcurrentHashMap.newKeySet();
            var pipeline = new PaymentPipeline(10, 2, payment -> processed.add(payment.id()));

            for (int i = 0; i < 50; i++) {
                assertThat(pipeline.submit(new Payment("p-" + i, "40817", BigDecimal.TEN))).isTrue();
            }
            while (processed.size() < 50) {
                Thread.sleep(5);
            }

            assertThat(pipeline.stop(Duration.ofSeconds(5))).isTrue();
            assertThat(processed).hasSize(50);
        });
    }

    @Test
    void paymentsAreRefusedAfterStop() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var pipeline = new PaymentPipeline(10, 2, payment -> { });
            assertThat(pipeline.stop(Duration.ofSeconds(5))).isTrue();

            assertThat(pipeline.submit(new Payment("p-1", "40817", BigDecimal.ONE))).isFalse();
        });
    }
}
