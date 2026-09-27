package ru.gits.task.medicine.t06;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Moves appointments to another time.
 */
public final class RescheduleService {

    private final Schedule schedule;

    public RescheduleService(Schedule schedule) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
    }

    /**
     * Moves the appointment to another time of the same doctor.
     *
     * @return the slot the patient is booked on now
     * @throws IllegalStateException when there is no appointment in the slot or the new time is taken
     */
    public AppointmentSlot reschedule(AppointmentSlot slot, LocalDateTime newTime) {
        if (schedule.patientAt(slot).isEmpty()) {
            throw new IllegalStateException("No appointment at " + slot);
        }
        if (schedule.isTaken(new AppointmentSlot(slot.doctorId(), newTime))) {
            throw new IllegalStateException("Doctor " + slot.doctorId() + " is busy at " + newTime);
        }
        slot.moveTo(newTime);
        return slot;
    }
}
