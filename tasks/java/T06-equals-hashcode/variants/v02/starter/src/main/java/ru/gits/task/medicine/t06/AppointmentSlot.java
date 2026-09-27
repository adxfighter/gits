package ru.gits.task.medicine.t06;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A doctor's appointment slot.
 */
public final class AppointmentSlot {

    private final String doctorId;
    private LocalDateTime time;

    public AppointmentSlot(String doctorId, LocalDateTime time) {
        this.doctorId = Objects.requireNonNull(doctorId, "doctorId");
        this.time = Objects.requireNonNull(time, "time");
    }

    public String doctorId() {
        return doctorId;
    }

    public LocalDateTime time() {
        return time;
    }

    /** Moves the slot to another time. */
    public void moveTo(LocalDateTime newTime) {
        this.time = Objects.requireNonNull(newTime, "newTime");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AppointmentSlot that && doctorId.equals(that.doctorId) && time.equals(that.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(doctorId, time);
    }

    @Override
    public String toString() {
        return doctorId + "@" + time;
    }
}
