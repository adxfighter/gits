package ru.gits.task.medicine.t04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DoctorContextHiddenTest {

    private static final Doctor THERAPIST = new Doctor("E-100", "терапевт");
    private static final Doctor CARDIOLOGIST = new Doctor("E-200", "кардиолог");
    private static final Doctor RADIOLOGIST = new Doctor("E-300", "рентгенолог");

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final ConsultationService service = new ConsultationService();

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void attendingDoctorActsAgainAfterAConsultation() throws Exception {
        inPool(() -> DoctorContext.runAs(THERAPIST, () -> {
            service.record("осмотр");
            service.consult(CARDIOLOGIST, () -> {
                service.record("заключение по ЭКГ");
                return null;
            });
            service.record("назначение");
        }));

        assertThat(service.journal())
                .containsExactly("E-100: осмотр", "E-200: заключение по ЭКГ", "E-100: назначение");
    }

    @Test
    void threeLevelsOfNestingAreRestoredInOrder() throws Exception {
        inPool(() -> DoctorContext.runAs(THERAPIST, () -> {
            service.consult(CARDIOLOGIST, () -> {
                service.consult(RADIOLOGIST, () -> {
                    service.record("снимок");
                    return null;
                });
                service.record("заключение");
                return null;
            });
            service.record("итог");
        }));

        assertThat(service.journal()).containsExactly("E-300: снимок", "E-200: заключение", "E-100: итог");
        assertThat(inPool(DoctorContext::current)).isEmpty();
    }

    @Test
    void failedConsultationRestoresTheAttendingDoctor() throws Exception {
        Optional<Doctor> afterFailure = inPool(() -> DoctorContext.runAs(THERAPIST, () -> {
            try {
                service.consult(CARDIOLOGIST, () -> {
                    throw new IllegalStateException("ЭКГ недоступна");
                });
            } catch (IllegalStateException expected) {
                // the attending doctor carries on
            }
            return DoctorContext.current();
        }));

        assertThat(afterFailure).contains(THERAPIST);
    }

    @Test
    void failedActionLeavesNoDoctorInThePooledThread() throws Exception {
        var failed = pool.submit(() -> DoctorContext.runAs(CARDIOLOGIST, () -> {
            throw new IllegalArgumentException("неверные данные");
        }));
        assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(inPool(DoctorContext::current)).isEmpty();
    }

    @Test
    void actionErrorReachesTheCaller() {
        assertThatThrownBy(() -> DoctorContext.runAs(THERAPIST, () -> {
            throw new IllegalStateException("карта заблокирована");
        })).isInstanceOf(IllegalStateException.class).hasMessage("карта заблокирована");
        assertThat(DoctorContext.current()).isEmpty();
    }

    @Test
    void sequentialRequestsInThePoolAreIndependent() throws Exception {
        for (int i = 0; i < 200; i++) {
            Doctor doctor = new Doctor("E-" + i, "терапевт");
            assertThat(inPool(() -> DoctorContext.runAs(doctor, DoctorContext::current))).contains(doctor);
            assertThat(inPool(DoctorContext::current)).isEmpty();
        }
    }

    private <T> T inPool(Callable<T> task) throws Exception {
        return pool.submit(task).get(5, TimeUnit.SECONDS);
    }

    private void inPool(Runnable task) throws Exception {
        pool.submit(task).get(5, TimeUnit.SECONDS);
    }
}
