package ru.gits.task.medicine.t04;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Records actions on patient cards on behalf of the current doctor and runs consultations with colleagues.
 */
public final class ConsultationService {

    private final List<String> journal = new ArrayList<>();

    /** Writes a journal entry "doctor: action" for the doctor currently in {@link DoctorContext}. */
    public synchronized void record(String action) {
        Doctor doctor = DoctorContext.current()
                .orElseThrow(() -> new IllegalStateException("Action without a doctor: " + action));
        journal.add(doctor.employeeId() + ": " + action);
    }

    /** Asks a colleague for an opinion; the colleague's actions are recorded on the colleague's behalf. */
    public <T> T consult(Doctor consultant, Supplier<T> opinion) {
        Objects.requireNonNull(consultant, "consultant");
        return DoctorContext.runAs(consultant, opinion);
    }

    public synchronized List<String> journal() {
        return List.copyOf(journal);
    }
}
