package ru.gits.task.medicine.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LabResultsCollectorVisibleTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    /** Laboratory stub: answers when the test completes the future. */
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

    private final ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lab-pool");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void allLaboratoriesAnswered() throws Exception {
        var invitro = new ManualLab("invitro");
        var helix = new ManualLab("helix");
        var gemotest = new ManualLab("gemotest");
        var citilab = new ManualLab("citilab");

        var report = new LabResultsCollector(List.of(invitro, helix, gemotest, citilab), pool, TIMEOUT).collect("R-1");
        invitro.answer.complete(new LabResult("HGB", "132 g/L"));
        helix.answer.complete(new LabResult("GLU", "5.1 mmol/L"));
        gemotest.answer.complete(new LabResult("TSH", "2.3 mIU/L"));
        citilab.answer.complete(new LabResult("CRP", "3 mg/L"));

        LabReport result = report.get(2, TimeUnit.SECONDS);
        assertThat(result.results()).isEqualTo(Map.of(
                "invitro", new LabResult("HGB", "132 g/L"),
                "helix", new LabResult("GLU", "5.1 mmol/L"),
                "gemotest", new LabResult("TSH", "2.3 mIU/L"),
                "citilab", new LabResult("CRP", "3 mg/L")));
        assertThat(result.failures()).isEmpty();
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void reportWaitsForAllLaboratories() throws Exception {
        var invitro = new ManualLab("invitro");
        var helix = new ManualLab("helix");

        var report = new LabResultsCollector(List.of(invitro, helix), pool, Duration.ofSeconds(5)).collect("R-2");
        invitro.answer.complete(new LabResult("HGB", "140 g/L"));
        assertThat(report).isNotDone();

        helix.answer.complete(new LabResult("GLU", "4.8 mmol/L"));
        assertThat(report.get(2, TimeUnit.SECONDS).results()).hasSize(2);
    }
}
