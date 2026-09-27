package ru.gits.task.telecom.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class CdrWriterVisibleTest {

    @Test
    void recordsAreSaved() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Set<String> saved = ConcurrentHashMap.newKeySet();
            var writer = new CdrWriter(2, record -> saved.add(record.id()));

            writer.accept(new CallRecord("c-1", "79001112233", "79004445566", 60));
            writer.accept(new CallRecord("c-2", "79001112233", "79007778899", 15));

            assertThat(writer.close(Duration.ofSeconds(5))).isTrue();
            assertThat(saved).containsExactlyInAnyOrder("c-1", "c-2");
        });
    }

    @Test
    void recordsAreRejectedAfterClose() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var writer = new CdrWriter(2, record -> { });
            assertThat(writer.close(Duration.ofSeconds(5))).isTrue();

            assertThatThrownBy(() -> writer.accept(new CallRecord("c-1", "1", "2", 1)))
                    .isInstanceOf(IllegalStateException.class);
        });
    }
}
