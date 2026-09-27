package ru.gits.task.medicine.t10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LabQueueHiddenTest {

    /** Analyzer that records finished analyses; while gated, every analysis waits for the gate. */
    static final class RecordingAnalyzer implements Analyzer {
        final Set<String> analyzed = ConcurrentHashMap.newKeySet();
        final Set<String> started = ConcurrentHashMap.newKeySet();
        final CountDownLatch gate;

        RecordingAnalyzer(boolean gated) {
            gate = new CountDownLatch(gated ? 1 : 0);
        }

        @Override
        public void analyze(Sample sample) throws InterruptedException {
            started.add(sample.barcode());
            gate.await();
            if (Thread.interrupted()) {
                throw new InterruptedException("analysis aborted");
            }
            if (!analyzed.add(sample.barcode())) {
                throw new AssertionError("analyzed twice: " + sample.barcode());
            }
        }
    }

    private static List<Sample> submitFromTwoPoints(LabQueue lab, int perPoint) throws InterruptedException {
        List<Sample> accepted = new ArrayList<>();
        for (int i = 0; i < perPoint; i++) {
            for (int point = 0; point < 2; point++) {
                Sample sample = new Sample("pt" + point + "-" + i, "P-" + i, "CBC");
                lab.submit(sample);
                accepted.add(sample);
            }
        }
        return accepted;
    }

    private static Set<String> barcodes(List<Sample> samples) {
        return samples.stream().map(Sample::barcode).collect(Collectors.toSet());
    }

    private static void assertNothingLost(List<Sample> accepted, Set<String> analyzed, List<Sample> returned) {
        Set<String> returnedBarcodes = barcodes(returned);
        assertThat(returned).as("returned samples are distinct").hasSameSizeAs(returnedBarcodes);
        assertThat(Collections.disjoint(returnedBarcodes, analyzed)).as("analyzed samples are not returned").isTrue();
        Set<String> all = new HashSet<>(analyzed);
        all.addAll(returnedBarcodes);
        assertThat(all).isEqualTo(barcodes(accepted));
    }

    @Test
    void emergencyStopWithBusyThreadsFinishes() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var analyzer = new RecordingAnalyzer(true);
            var lab = new LabQueue(20, 2, analyzer);
            List<Sample> accepted = submitFromTwoPoints(lab, 5);
            while (analyzer.started.size() < 2) {
                Thread.sleep(5);
            }

            List<Sample> returned = lab.shutdownNow();

            assertThat(lab.awaitTermination(Duration.ofSeconds(1))).isTrue();
            assertThat(barcodes(returned)).isEqualTo(barcodes(accepted));
        });
    }

    @Test
    void interruptedAnalysesAreReturned() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var analyzer = new RecordingAnalyzer(true);
            var lab = new LabQueue(20, 2, analyzer);
            lab.submit(new Sample("B-1", "P-1", "CBC"));
            lab.submit(new Sample("B-2", "P-2", "GLU"));
            while (analyzer.started.size() < 2) {
                Thread.sleep(5);
            }

            List<Sample> returned = lab.shutdownNow();

            assertThat(barcodes(returned)).containsExactlyInAnyOrder("B-1", "B-2");
            assertThat(analyzer.analyzed).isEmpty();
        });
    }

    @Test
    void emergencyStopOfIdleThreadsFinishes() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var lab = new LabQueue(20, 2, new RecordingAnalyzer(false));
            Thread.sleep(20);

            assertThat(lab.shutdownNow()).isEmpty();
            assertThat(lab.awaitTermination(Duration.ofSeconds(1))).isTrue();
        });
    }

    @Test
    void emergencyStopUnderLoadLosesNothing() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int round = 0; round < 10; round++) {
                var analyzer = new RecordingAnalyzer(false);
                var lab = new LabQueue(200, 2, analyzer);
                List<Sample> accepted = submitFromTwoPoints(lab, 100);

                List<Sample> returned = lab.shutdownNow();

                assertThat(lab.awaitTermination(Duration.ofSeconds(1))).as("round %d", round).isTrue();
                assertNothingLost(accepted, analyzer.analyzed, returned);
            }
        });
    }

    @Test
    void samplesAreRejectedAfterEmergencyStop() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var lab = new LabQueue(20, 2, new RecordingAnalyzer(false));
            lab.shutdownNow();

            assertThatThrownBy(() -> lab.submit(new Sample("B-1", "P-1", "CBC")))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void analyzerFailureDoesNotStopTheThread() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            Set<String> analyzed = ConcurrentHashMap.newKeySet();
            var lab = new LabQueue(20, 1, sample -> {
                if (sample.test().equals("BROKEN")) {
                    throw new IllegalStateException("cuvette jammed");
                }
                analyzed.add(sample.barcode());
            });

            lab.submit(new Sample("B-1", "P-1", "CBC"));
            lab.submit(new Sample("B-2", "P-2", "BROKEN"));
            lab.submit(new Sample("B-3", "P-3", "GLU"));
            lab.shutdown();

            assertThat(lab.awaitTermination(Duration.ofSeconds(5))).isTrue();
            assertThat(analyzed).containsExactlyInAnyOrder("B-1", "B-3");
        });
    }
}
