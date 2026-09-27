package ru.gits.task.medicine.t02;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A hospital ward with a fixed number of beds. The ward's state is protected by its own monitor.
 */
public final class Ward {

    private final int number;
    private final int beds;
    private final Set<Patient> patients = new LinkedHashSet<>();

    public Ward(int number, int beds) {
        if (beds <= 0) {
            throw new IllegalArgumentException("A ward needs at least one bed");
        }
        this.number = number;
        this.beds = beds;
    }

    public int number() {
        return number;
    }

    public int beds() {
        return beds;
    }

    public synchronized int occupied() {
        return patients.size();
    }

    public synchronized boolean hasFreeBed() {
        return patients.size() < beds;
    }

    /** Admits a patient; throws when there is no free bed. */
    public synchronized void admit(Patient patient) {
        Objects.requireNonNull(patient, "patient");
        if (patients.size() >= beds) {
            throw new IllegalStateException("No free beds in ward " + number);
        }
        patients.add(patient);
    }

    /**
     * Moves the longest-staying patient to another ward.
     *
     * @return the moved patient, or empty when this ward is empty or the target has no free bed
     */
    public synchronized Optional<Patient> transferTo(Ward target) {
        Iterator<Patient> longestStaying = patients.iterator();
        if (!longestStaying.hasNext() || !target.hasFreeBed()) {
            return Optional.empty();
        }
        Patient patient = longestStaying.next();
        target.admit(patient);
        longestStaying.remove();
        return Optional.of(patient);
    }
}
