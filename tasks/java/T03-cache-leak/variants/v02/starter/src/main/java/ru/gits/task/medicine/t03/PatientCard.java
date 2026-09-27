package ru.gits.task.medicine.t03;

import java.util.List;
import java.util.Objects;

/**
 * Electronic patient card as returned by the regional registry.
 *
 * @param patientId  insurance policy number
 * @param fullName   patient name
 * @param allergies  known allergies, may be empty
 */
public record PatientCard(String patientId, String fullName, List<String> allergies) {

    public PatientCard {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(fullName, "fullName");
        allergies = List.copyOf(allergies);
    }
}
