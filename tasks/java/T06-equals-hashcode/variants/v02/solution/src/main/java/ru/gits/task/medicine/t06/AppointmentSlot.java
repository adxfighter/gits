package ru.gits.task.medicine.t06;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A doctor's appointment slot. Immutable: it is used as a key of the schedule.
 */
public record AppointmentSlot(String doctorId, LocalDateTime time) {

    public AppointmentSlot {
        Objects.requireNonNull(doctorId, "doctorId");
        Objects.requireNonNull(time, "time");
    }

    /** Returns the slot of the same doctor at another time. */
    public AppointmentSlot at(LocalDateTime newTime) {
        return new AppointmentSlot(doctorId, newTime);
    }

    @Override
    public String toString() {
        return doctorId + "@" + time;
    }
}
