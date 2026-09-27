package ru.gits.task.telecom.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CdrWriterHiddenTest {

    /** Store that holds every save until the test opens the gate. */
    static final class GatedStore implements CdrStore {
        final CountDownLatch gate = new CountDownLatch(1);
        /** Counted down by the first two saves that have started. */
        final CountDownLatch twoStarted = new CountDownLatch(2);
        final Map<String, AtomicInteger> saved = new ConcurrentHashMap<>();

        @Override
        public void save(CallRecord record) throws InterruptedException {
            twoStarted.countDown();
            gate.await();
            Thread.yield();   // let the other threads run in the middle of accept and close
            saved.computeIfAbsent(record.id(), id -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Two switches send records concurrently; returns the ids sent. */
    private static int produce(CdrWriter writer, int perProducer) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> producers = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            int producer = p;
            Thread thread = daemon("switch-" + p, () -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    return;
                }
                for (int i = 0; i < perProducer; i++) {
                    writer.accept(new CallRecord("s" + producer + "-" + i, "7900" + producer, "7911" + i, i));
                }
            });
            producers.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread producer : producers) {
            producer.join();
        }
        return 2 * perProducer;
    }

    @Test
    void queuedRecordsAreSavedOnClose() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            var store = new GatedStore();
            var writer = new CdrWriter(2, store);
            int sent = produce(writer, 500);

            var closing = CompletableFuture.supplyAsync(() -> {
                try {
                    return writer.close(Duration.ofSeconds(5));
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
            });
            Thread.sleep(50);
            store.gate.countDown();

            assertThat(closing.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(store.saved).hasSize(sent);
            assertThat(store.saved.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        });
    }

    @Test
    void recordsBeingSavedAreNotInterrupted() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            var store = new GatedStore();
            var writer = new CdrWriter(2, store);
            writer.accept(new CallRecord("c-1", "1", "2", 10));
            writer.accept(new CallRecord("c-2", "1", "3", 20));
            assertThat(store.twoStarted.await(5, TimeUnit.SECONDS)).as("both records are being saved").isTrue();

            var closing = CompletableFuture.supplyAsync(() -> {
                try {
                    return writer.close(Duration.ofSeconds(5));
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
            });
            Thread.sleep(50);
            store.gate.countDown();

            assertThat(closing.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(store.saved).containsOnlyKeys("c-1", "c-2");
        });
    }

    @Test
    void closeReportsTimeoutWhileRecordsAreStillSaving() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            var store = new GatedStore();
            var writer = new CdrWriter(2, store);
            produce(writer, 20);

            assertThat(writer.close(Duration.ofMillis(100))).isFalse();

            store.gate.countDown();
            assertThat(writer.close(Duration.ofSeconds(5))).isTrue();
            assertThat(store.saved).hasSize(40);
        });
    }

    @Test
    void recordsSentWhileClosingAreEitherSavedOrRejected() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int round = 0; round < 30; round++) {
                var store = new GatedStore();
                store.gate.countDown();
                var writer = new CdrWriter(2, store);
                AtomicInteger accepted = new AtomicInteger();
                Queue<String> refusals = new ConcurrentLinkedQueue<>();

                List<Thread> senders = new ArrayList<>();
                for (int s = 0; s < 2; s++) {
                    String prefix = "r" + round + "-s" + s + "-";
                    Thread sender = daemon("switch-" + s, () -> {
                        for (int i = 0; i < 1_000_000; i++) {
                            try {
                                writer.accept(new CallRecord(prefix + i, "1", "2", 1));
                                accepted.incrementAndGet();
                            } catch (RuntimeException refused) {
                                refusals.add(refused.getClass().getName());
                                return;
                            }
                        }
                    });
                    senders.add(sender);
                    sender.start();
                }
                Thread.sleep(5);

                assertThat(writer.close(Duration.ofSeconds(5))).as("round %d", round).isTrue();
                for (Thread sender : senders) {
                    sender.join(5_000);
                    assertThat(sender.isAlive()).as("round %d", round).isFalse();
                }
                assertThat(refusals).as("round %d: refusal after close", round)
                        .hasSize(2)
                        .containsOnly(IllegalStateException.class.getName());
                assertThat(store.saved).as("round %d", round).hasSize(accepted.get());
            }
        });
    }

    @Test
    void manyRecordsWithoutDelay() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            var store = new GatedStore();
            store.gate.countDown();
            var writer = new CdrWriter(2, store);

            int sent = produce(writer, 5_000);

            assertThat(writer.close(Duration.ofSeconds(5))).isTrue();
            assertThat(store.saved).hasSize(sent);
        });
    }
}
