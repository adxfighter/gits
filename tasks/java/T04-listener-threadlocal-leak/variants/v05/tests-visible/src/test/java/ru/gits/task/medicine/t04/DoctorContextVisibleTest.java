package ru.gits.task.medicine.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class DoctorContextVisibleTest {

    private static final Doctor THERAPIST = new Doctor("E-100", "терапевт");

    @Test
    void actionIsRecordedOnBehalfOfTheDoctor() throws Exception {
        var service = new ConsultationService();

        inFreshThread(() -> {
            DoctorContext.runAs(THERAPIST, () -> service.record("осмотр"));
            return null;
        });

        assertThat(service.journal()).containsExactly("E-100: осмотр");
    }

    @Test
    void runAsReturnsTheActionResult() throws Exception {
        String result = inFreshThread(() -> DoctorContext.runAs(THERAPIST,
                () -> DoctorContext.current().orElseThrow().specialty()));

        assertThat(result).isEqualTo("терапевт");
    }

    private static <T> T inFreshThread(Callable<T> task) throws Exception {
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            return thread.submit(task).get(5, TimeUnit.SECONDS);
        } finally {
            thread.shutdownNow();
        }
    }
}
