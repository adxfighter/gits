package ru.gits.task.medicine.t02;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A hospital ward with a fixed number of beds. The ward's state is protected by its own monitor.
 * Lock protocol: when several wards must be locked, they are always locked in ascending ward number.
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
    public Optional<Patient> transferTo(Ward target) {
        Ward first = number < target.number ? this : target;
        Ward second = first == this ? target : this;
        synchronized (first) {
            synchronized (second) {
                return transferToLocked(target);
            }
        }
    }

    /** Caller must hold the monitors of this ward and the target. */
    private Optional<Patient> transferToLocked(Ward target) {
        Iterator<Patient> longestStaying = patients.iterator();
        if (!longestStaying.hasNext() || target.patients.size() >= target.beds) {
            return Optional.empty();
        }
        Patient patient = longestStaying.next();
        longestStaying.remove();
        target.patients.add(patient);
        return Optional.of(patient);
    }
}
