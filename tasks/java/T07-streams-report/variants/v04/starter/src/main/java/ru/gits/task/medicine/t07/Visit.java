package ru.gits.task.medicine.t07;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A patient's visit to the clinic. Several doctors may see the patient during one visit, or none.
 */
public record Visit(String visitId, String patientId, LocalDate date, List<String> doctorIds) {

    public Visit {
        Objects.requireNonNull(visitId, "visitId");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(date, "date");
        doctorIds = List.copyOf(doctorIds);
    }
}
