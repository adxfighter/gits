package ru.gits.task.energy.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class TelemetryIngestVisibleTest {

    @Test
    void readingsAreHandled() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Set<String> handled = ConcurrentHashMap.newKeySet();
            var ingest = new TelemetryIngest(100, 2, reading -> handled.add(reading.meterId()));

            for (int i = 0; i < 50; i++) {
                ingest.submit(new Reading("m-" + i, Instant.EPOCH, i));
            }

            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();
            assertThat(handled).hasSize(50);
        });
    }

    @Test
    void readingsAreRejectedAfterClose() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var ingest = new TelemetryIngest(10, 2, reading -> { });
            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();

            assertThatThrownBy(() -> ingest.submit(new Reading("m-1", Instant.EPOCH, 1)))
                    .isInstanceOf(IllegalStateException.class);
        });
    }
}
