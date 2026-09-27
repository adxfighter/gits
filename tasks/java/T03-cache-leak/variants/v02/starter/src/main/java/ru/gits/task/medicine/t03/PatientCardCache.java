package ru.gits.task.medicine.t03;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Caches patient cards loaded from the regional registry. Used by a single request thread of the clinic.
 */
public final class PatientCardCache {

    private final CardRegistry registry;
    private final int capacity;
    private final Map<String, PatientCard> cards = new HashMap<>();

    public PatientCardCache(CardRegistry registry, int capacity) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Card of a patient, loaded from the registry only when it is not cached. */
    public PatientCard card(String patientId) {
        Objects.requireNonNull(patientId, "patientId");
        PatientCard cached = cards.get(patientId);
        if (cached != null) {
            return cached;
        }
        PatientCard loaded = registry.load(patientId);
        store(patientId, loaded);
        return loaded;
    }

    /** Reloads a card from the registry, e.g. after a doctor added an allergy. */
    public PatientCard refresh(String patientId) {
        PatientCard loaded = registry.load(patientId);
        store(patientId, loaded);
        return loaded;
    }

    public int size() {
        return cards.size();
    }

    private void store(String patientId, PatientCard card) {
        if (cards.size() >= capacity) {
            // keep memory bounded
            cards.clear();
        }
        cards.put(patientId, card);
    }
}
