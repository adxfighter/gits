package ru.gits.task.medicine.t06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class RescheduleHiddenTest {

    private static final LocalDateTime MONDAY_10 = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final LocalDateTime MONDAY_11 = MONDAY_10.plusHours(1);
    private static final LocalDateTime TUESDAY_9 = LocalDateTime.of(2026, 10, 6, 9, 0);

    @Test
    void patientIsFoundAtTheNewTime() {
        var schedule = new Schedule();
        var slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");

        new RescheduleService(schedule).reschedule(slot, TUESDAY_9);

        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", TUESDAY_9))).contains("patient-1");
        assertThat(schedule.size()).isEqualTo(1);
    }

    @Test
    void oldTimeIsFreedAndCanBeBookedAgain() {
        var schedule = new Schedule();
        var slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");

        new RescheduleService(schedule).reschedule(slot, TUESDAY_9);

        assertThat(schedule.isTaken(new AppointmentSlot("dr-ivanova", MONDAY_10))).isFalse();
        schedule.book(new AppointmentSlot("dr-ivanova", MONDAY_10), "patient-2");
        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", MONDAY_10))).contains("patient-2");
        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", TUESDAY_9))).contains("patient-1");
        assertThat(schedule.size()).isEqualTo(2);
    }

    @Test
    void returnedSlotDescribesTheNewAppointment() {
        var schedule = new Schedule();
        var slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");

        AppointmentSlot moved = new RescheduleService(schedule).reschedule(slot, TUESDAY_9);

        assertThat(moved.doctorId()).isEqualTo("dr-ivanova");
        assertThat(moved.time()).isEqualTo(TUESDAY_9);
        assertThat(schedule.patientAt(moved)).contains("patient-1");
    }

    @Test
    void chainOfReschedulesKeepsTheScheduleConsistent() {
        var schedule = new Schedule();
        var service = new RescheduleService(schedule);
        AppointmentSlot slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");

        for (int day = 1; day <= 20; day++) {
            slot = service.reschedule(slot, MONDAY_10.plusDays(day));
        }

        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", MONDAY_10.plusDays(20)))).contains("patient-1");
        for (int day = 0; day < 20; day++) {
            assertThat(schedule.isTaken(new AppointmentSlot("dr-ivanova", MONDAY_10.plusDays(day))))
                    .as("day %d", day).isFalse();
        }
        assertThat(schedule.size()).isEqualTo(1);
    }

    @Test
    void rescheduleToATakenTimeIsRejectedAndNothingChanges() {
        var schedule = new Schedule();
        var slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");
        schedule.book(new AppointmentSlot("dr-ivanova", MONDAY_11), "patient-2");

        assertThatThrownBy(() -> new RescheduleService(schedule).reschedule(slot, MONDAY_11))
                .isInstanceOf(IllegalStateException.class);

        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", MONDAY_10))).contains("patient-1");
        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", MONDAY_11))).contains("patient-2");
        assertThat(schedule.size()).isEqualTo(2);
    }

    @Test
    void anotherDoctorAtTheSameTimeIsNotAConflict() {
        var schedule = new Schedule();
        var slot = new AppointmentSlot("dr-ivanova", MONDAY_10);
        schedule.book(slot, "patient-1");
        schedule.book(new AppointmentSlot("dr-petrov", TUESDAY_9), "patient-2");

        new RescheduleService(schedule).reschedule(slot, TUESDAY_9);

        assertThat(schedule.patientAt(new AppointmentSlot("dr-ivanova", TUESDAY_9))).contains("patient-1");
        assertThat(schedule.patientAt(new AppointmentSlot("dr-petrov", TUESDAY_9))).contains("patient-2");
    }
}
