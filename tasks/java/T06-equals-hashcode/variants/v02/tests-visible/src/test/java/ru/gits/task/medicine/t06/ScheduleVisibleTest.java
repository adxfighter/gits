package ru.gits.task.medicine.t06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ScheduleVisibleTest {

    private static final LocalDateTime MONDAY_10 = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void bookedPatientIsFoundByAnEqualSlot() {
        var schedule = new Schedule();
        schedule.book(new AppointmentSlot("dr-ivanova", MONDAY_10), "patient-1");

        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", MONDAY_10))).contains("patient-1");
        assertThat(schedule.patientAt(new AppointmentSlot("dr-petrov", MONDAY_10))).isEmpty();
    }

    @Test
    void takenSlotCannotBeBookedTwice() {
        var schedule = new Schedule();
        schedule.book(new AppointmentSlot("dr-ivanova", MONDAY_10), "patient-1");

        assertThatThrownBy(() -> schedule.book(new AppointmentSlot("dr-ivanova", MONDAY_10), "patient-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(schedule.size()).isEqualTo(1);
    }
}
