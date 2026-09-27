package ru.gits.task.bank.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PaymentPipelineHiddenTest {

    private static final int PRODUCERS = 4;

    /** Records processed payments; optionally holds each one until the gate opens. */
    static final class Ledger {
        final Map<String, AtomicInteger> processed = new ConcurrentHashMap<>();
        final CountDownLatch gate;

        Ledger(boolean gated) {
            gate = new CountDownLatch(gated ? 1 : 0);
        }

        void process(Payment payment) {
            try {
                gate.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;   // interrupted processing: the payment is not booked
            }
            processed.computeIfAbsent(payment.id(), id -> new AtomicInteger()).incrementAndGet();
        }
    }

    /** Gateway threads that submit payments until refused; accepted ids are collected. */
    static final class Gateway {
        final Set<String> accepted = ConcurrentHashMap.newKeySet();
        final List<Thread> threads = new ArrayList<>();

        Gateway(PaymentPipeline pipeline, int perProducer) {
            for (int p = 0; p < PRODUCERS; p++) {
                int producer = p;
                Thread thread = new Thread(() -> {
                    try {
                        for (int i = 0; i < perProducer; i++) {
                            String id = "g" + producer + "-" + i;
                            if (!pipeline.submit(new Payment(id, "408178100" + producer, BigDecimal.valueOf(i)))) {
                                return;
                            }
                            accepted.add(id);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }, "gateway-" + p);
                thread.setDaemon(true);
                threads.add(thread);
                thread.start();
            }
        }

        boolean finishedWithin(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            for (Thread thread : threads) {
                thread.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
                if (thread.isAlive()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static CompletableFuture<Boolean> stopAsync(PaymentPipeline pipeline, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return pipeline.stop(timeout);
            } catch (InterruptedException interrupted) {
                throw new IllegalStateException(interrupted);
            }
        });
    }

    private static void assertExactlyOnce(Ledger ledger, Gateway gateway) {
        assertThat(ledger.processed.keySet()).isEqualTo(gateway.accepted);
        assertThat(ledger.processed.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test
    void producersWaitingForSpaceAreReleasedByStop() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            var ledger = new Ledger(true);
            var pipeline = new PaymentPipeline(5, 2, ledger::process);
            var gateway = new Gateway(pipeline, 100);
            Thread.sleep(100);   // the queue is full, all producers are waiting

            var stopping = stopAsync(pipeline, Duration.ofSeconds(5));
            Thread.sleep(50);
            ledger.gate.countDown();

            assertThat(gateway.finishedWithin(Duration.ofSeconds(3))).as("gateway threads return").isTrue();
            assertThat(stopping.get(10, TimeUnit.SECONDS)).isTrue();
            assertExactlyOnce(ledger, gateway);
            assertThat(gateway.accepted.size()).isLessThan(PRODUCERS * 100);
        });
    }

    @Test
    void acceptedPaymentsAreProcessedWhenStoppedUnderLoad() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (int round = 0; round < 5; round++) {
                var ledger = new Ledger(false);
                var pipeline = new PaymentPipeline(16, 2, ledger::process);
                var gateway = new Gateway(pipeline, 20_000);
                Thread.sleep(20);

                assertThat(pipeline.stop(Duration.ofSeconds(5))).as("round %d", round).isTrue();
                assertThat(gateway.finishedWithin(Duration.ofSeconds(3))).as("round %d", round).isTrue();
                assertExactlyOnce(ledger, gateway);
            }
        });
    }

    @Test
    void stopReportsTimeoutWhileProcessingIsHeld() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            var ledger = new Ledger(true);
            var pipeline = new PaymentPipeline(5, 2, ledger::process);
            assertThat(pipeline.submit(new Payment("p-1", "40817", BigDecimal.ONE))).isTrue();
            assertThat(pipeline.submit(new Payment("p-2", "40817", BigDecimal.ONE))).isTrue();
            assertThat(pipeline.submit(new Payment("p-3", "40817", BigDecimal.ONE))).isTrue();

            assertThat(pipeline.stop(Duration.ofMillis(100))).isFalse();
            ledger.gate.countDown();
            assertThat(pipeline.stop(Duration.ofSeconds(5))).isTrue();
            assertThat(ledger.processed).containsOnlyKeys("p-1", "p-2", "p-3");
        });
    }

    @Test
    void idlePipelineStopsQuickly() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var pipeline = new PaymentPipeline(5, 3, payment -> { });

            assertThat(pipeline.stop(Duration.ofSeconds(2))).isTrue();
        });
    }

    @Test
    void interruptedProducerGetsInterruptedException() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var ledger = new Ledger(true);
            var pipeline = new PaymentPipeline(1, 1, ledger::process);
            pipeline.submit(new Payment("p-1", "40817", BigDecimal.ONE));   // taken by the consumer, held
            pipeline.submit(new Payment("p-2", "40817", BigDecimal.ONE));   // fills the queue
            Thread.sleep(50);

            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> pipeline.submit(new Payment("p-3", "40817", BigDecimal.ONE)))
                        .isInstanceOf(InterruptedException.class);
            } finally {
                Thread.interrupted();
                ledger.gate.countDown();
            }
            assertThat(pipeline.stop(Duration.ofSeconds(5))).isTrue();
            assertThat(ledger.processed).containsOnlyKeys("p-1", "p-2");
        });
    }
}
