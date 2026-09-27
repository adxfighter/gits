package ru.gits.task.medicine.t07;

import java.util.List;

/**
 * Visits of one patient in the period.
 *
 * @param doctorIds       distinct doctors who saw the patient, ascending
 * @param distinctDoctors number of distinct doctors
 */
public record PatientSummary(String patientId, int visitCount, List<String> doctorIds, int distinctDoctors) {
}
