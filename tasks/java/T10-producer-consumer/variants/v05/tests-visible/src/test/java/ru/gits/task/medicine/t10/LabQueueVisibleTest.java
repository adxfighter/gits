package ru.gits.task.medicine.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class LabQueueVisibleTest {

    @Test
    void plannedShutdownAnalyzesEverything() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Set<String> analyzed = ConcurrentHashMap.newKeySet();
            var lab = new LabQueue(20, 2, sample -> analyzed.add(sample.barcode()));

            for (int i = 0; i < 100; i++) {
                lab.submit(new Sample("B-" + i, "P-" + i % 10, "CBC"));
            }
            lab.shutdown();

            assertThat(lab.awaitTermination(Duration.ofSeconds(5))).isTrue();
            assertThat(analyzed).hasSize(100);
        });
    }

    @Test
    void samplesAreRejectedAfterShutdown() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var lab = new LabQueue(20, 2, sample -> { });
            lab.shutdown();

            assertThatThrownBy(() -> lab.submit(new Sample("B-1", "P-1", "CBC")))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(lab.awaitTermination(Duration.ofSeconds(5))).isTrue();
        });
    }
}
