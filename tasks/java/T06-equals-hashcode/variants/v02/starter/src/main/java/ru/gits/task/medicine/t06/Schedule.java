package ru.gits.task.medicine.t06;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Appointments of the clinic: slot -> patient id.
 */
public final class Schedule {

    private final Map<AppointmentSlot, String> appointments = new HashMap<>();

    /** Books a slot; fails if the slot is already taken. */
    public void book(AppointmentSlot slot, String patientId) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(patientId, "patientId");
        if (appointments.containsKey(slot)) {
            throw new IllegalStateException("Slot " + slot + " is already taken");
        }
        appointments.put(slot, patientId);
    }

    public Optional<String> patientAt(AppointmentSlot slot) {
        return Optional.ofNullable(appointments.get(slot));
    }

    public boolean isTaken(AppointmentSlot slot) {
        return appointments.containsKey(slot);
    }

    /** Cancels an appointment and returns the patient who was booked. */
    public Optional<String> cancel(AppointmentSlot slot) {
        return Optional.ofNullable(appointments.remove(slot));
    }

    public int size() {
        return appointments.size();
    }
}
