package ru.gits.task.medicine.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LabResultsCollectorHiddenTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    static final class ManualLab implements Lab {
        private final String name;
        final CompletableFuture<LabResult> answer = new CompletableFuture<>();

        ManualLab(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<LabResult> result(String orderId) {
            return answer;
        }
    }

    static final class ThrowingLab implements Lab {
        @Override
        public String name() {
            return "citilab";
        }

        @Override
        public CompletableFuture<LabResult> result(String orderId) {
            throw new IllegalStateException("TLS handshake failed");
        }
    }

    private final List<ManualLab> created = new ArrayList<>();

    private final ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lab-pool");
        thread.setDaemon(true);
        return thread;
    });

    private ManualLab lab(String name) {
        var lab = new ManualLab(name);
        created.add(lab);
        return lab;
    }

    @AfterEach
    void release() {
        created.forEach(lab -> lab.answer.complete(new LabResult("X", "late")));
        pool.shutdownNow();
    }

    private LabResultsCollector collector(Lab... labs) {
        return new LabResultsCollector(List.of(labs), pool, TIMEOUT);
    }

    @Test
    void failureReasonIsKeptAndReportIsIncomplete() throws Exception {
        var invitro = lab("invitro");
        var helix = lab("helix");

        var report = collector(invitro, helix).collect("R-1");
        invitro.answer.complete(new LabResult("HGB", "132 g/L"));
        helix.answer.completeExceptionally(new IllegalStateException("sample hemolyzed"));

        LabReport result = report.get(1, TimeUnit.SECONDS);
        assertThat(result.results()).isEqualTo(Map.of("invitro", new LabResult("HGB", "132 g/L")));
        assertThat(result.failures()).isEqualTo(Map.of("helix", "sample hemolyzed"));
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void silentLaboratoryIsReportedAsTimeout() throws Exception {
        var invitro = lab("invitro");
        var silent = lab("gemotest");

        var report = collector(invitro, silent).collect("R-2");
        invitro.answer.complete(new LabResult("TSH", "2.3 mIU/L"));

        LabReport result = report.get(1, TimeUnit.SECONDS);
        assertThat(result.results()).containsOnlyKeys("invitro");
        assertThat(result.failures()).isEqualTo(Map.of("gemotest", "timeout"));
    }

    @Test
    void laboratoryThrowingRightAwayIsReportedAsAFailure() throws Exception {
        var invitro = lab("invitro");
        invitro.answer.complete(new LabResult("HGB", "120 g/L"));

        LabReport result = collector(invitro, new ThrowingLab()).collect("R-3").get(1, TimeUnit.SECONDS);

        assertThat(result.results()).containsOnlyKeys("invitro");
        assertThat(result.failures()).isEqualTo(Map.of("citilab", "TLS handshake failed"));
    }

    @Test
    void wrappedFailureIsUnwrapped() throws Exception {
        CompletableFuture<LabResult> failingStage = CompletableFuture.completedFuture("raw")
                .thenApply(raw -> {
                    throw new IllegalArgumentException("analyzer offline");
                });
        Lab helix = new Lab() {
            @Override
            public String name() {
                return "helix";
            }

            @Override
            public CompletableFuture<LabResult> result(String orderId) {
                return failingStage;
            }
        };

        LabReport result = collector(helix).collect("R-4").get(1, TimeUnit.SECONDS);

        assertThat(result.failures()).isEqualTo(Map.of("helix", "analyzer offline"));
    }

    @Test
    void waitingForLaboratoriesDoesNotOccupyThePool() throws Exception {
        var invitro = lab("invitro");
        var helix = lab("helix");
        var gemotest = lab("gemotest");
        var citilab = lab("citilab");
        var collector = new LabResultsCollector(List.of(invitro, helix, gemotest, citilab), pool, Duration.ofSeconds(5));

        var report = collector.collect("R-5");
        CountDownLatch otherTaskRan = new CountDownLatch(1);
        pool.execute(otherTaskRan::countDown);

        assertThat(otherTaskRan.await(1, TimeUnit.SECONDS))
                .as("another task of the pool runs while laboratories are silent")
                .isTrue();
        created.forEach(lab -> lab.answer.complete(new LabResult("HGB", "1")));
        assertThat(report.get(1, TimeUnit.SECONDS).isComplete()).isTrue();
    }

    @Test
    void mixedOutcomesOfFourLaboratories() throws Exception {
        var invitro = lab("invitro");
        var helix = lab("helix");
        var silent = lab("gemotest");
        var citilab = lab("citilab");

        var report = collector(invitro, helix, silent, citilab).collect("R-6");
        citilab.answer.complete(new LabResult("CRP", "3 mg/L"));
        helix.answer.completeExceptionally(new RuntimeException());
        invitro.answer.complete(new LabResult("HGB", "132 g/L"));

        LabReport result = report.get(1, TimeUnit.SECONDS);
        assertThat(result.results()).containsOnlyKeys("invitro", "citilab");
        assertThat(result.failures()).isEqualTo(Map.of("helix", "RuntimeException", "gemotest", "timeout"));
    }

    @Test
    void resultAfterTheTimeoutIsNotAdded() throws Exception {
        var invitro = lab("invitro");
        var late = lab("helix");

        var report = collector(invitro, late).collect("R-7");
        invitro.answer.complete(new LabResult("HGB", "132 g/L"));
        LabReport result = report.get(1, TimeUnit.SECONDS);
        late.answer.complete(new LabResult("GLU", "5.0 mmol/L"));

        assertThat(report.join()).isEqualTo(result);
        assertThat(result.results()).containsOnlyKeys("invitro");
        assertThat(result.failures()).containsOnlyKeys("helix");
    }
}
