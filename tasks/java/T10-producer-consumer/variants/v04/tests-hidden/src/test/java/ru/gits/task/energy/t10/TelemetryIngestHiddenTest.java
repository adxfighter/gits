package ru.gits.task.energy.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TelemetryIngestHiddenTest {

    private final Map<String, AtomicInteger> handled = new ConcurrentHashMap<>();

    private void record(Reading reading) {
        handled.computeIfAbsent(reading.meterId(), id -> new AtomicInteger()).incrementAndGet();
    }

    /** Two channels send bursts of readings with pauses between bursts. */
    private static int twoChannels(TelemetryIngest ingest, int bursts, int perBurst, boolean pauses)
            throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> channels = new ArrayList<>();
        for (int c = 0; c < 2; c++) {
            int channel = c;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int burst = 0; burst < bursts; burst++) {
                        if (pauses) {
                            Thread.sleep(50);
                        }
                        for (int i = 0; i < perBurst; i++) {
                            ingest.submit(new Reading("c" + channel + "-b" + burst + "-" + i, Instant.EPOCH, i));
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "channel-" + c);
            thread.setDaemon(true);
            channels.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread channel : channels) {
            channel.join(Duration.ofSeconds(3));
            assertThat(channel.isAlive()).as("channel %s is blocked in submit", channel.getName()).isFalse();
        }
        return 2 * bursts * perBurst;
    }

    private void assertExactlyOnce(int expected) {
        assertThat(handled).hasSize(expected);
        assertThat(handled.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test
    void readingsAfterAPauseAreHandled() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            var ingest = new TelemetryIngest(50, 4, this::record);
            Thread.sleep(100);   // morning pause: nothing to do yet

            int sent = twoChannels(ingest, 1, 40, false);

            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();
            assertExactlyOnce(sent);
        });
    }

    @Test
    void burstsWithPausesAreAllHandled() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            var ingest = new TelemetryIngest(32, 4, this::record);

            int sent = twoChannels(ingest, 5, 300, true);

            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();
            assertExactlyOnce(sent);
        });
    }

    @Test
    void fullQueueWithManyConsumersClosesInTime() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            var ingest = new TelemetryIngest(8, 4, this::record);

            int sent = twoChannels(ingest, 1, 5_000, false);

            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();
            assertExactlyOnce(sent);
        });
    }

    @Test
    void closingAnIdleIngestFinishesQuickly() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var ingest = new TelemetryIngest(8, 4, this::record);
            Thread.sleep(50);

            assertThat(ingest.close(Duration.ofSeconds(2))).isTrue();
            assertThat(handled).isEmpty();
        });
    }

    @Test
    void singleReadingWithFourConsumers() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int round = 0; round < 20; round++) {
                handled.clear();
                var ingest = new TelemetryIngest(8, 4, this::record);
                Thread.sleep(20);
                ingest.submit(new Reading("m-" + round, Instant.EPOCH, 1));

                assertThat(ingest.close(Duration.ofSeconds(2))).as("round %d", round).isTrue();
                assertExactlyOnce(1);
            }
        });
    }

    @Test
    void brokenReadingDoesNotStopConsumers() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var ingest = new TelemetryIngest(16, 2, reading -> {
                if (reading.wh() < 0) {
                    throw new IllegalArgumentException("negative reading");
                }
                record(reading);
            });
            Thread.sleep(20);
            for (int i = 0; i < 100; i++) {
                ingest.submit(new Reading("m-" + i, Instant.EPOCH, i % 10 == 0 ? -1 : i));
            }

            assertThat(ingest.close(Duration.ofSeconds(5))).isTrue();
            assertExactlyOnce(90);
        });
    }
}
