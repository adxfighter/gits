package ru.gits.task.medicine.t07;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Visit summary for the insurance company.
 */
public final class VisitReport {

    /**
     * Summarizes visits from {@code from} to {@code to} inclusive, one line per patient ordered by patient id.
     */
    public List<PatientSummary> summarize(List<Visit> visits, LocalDate from, LocalDate to) {
        Map<String, List<Visit>> byPatient = visits.stream()
                .filter(visit -> !visit.date().isBefore(from) && visit.date().isBefore(to))
                .collect(Collectors.groupingBy(Visit::patientId, TreeMap::new, Collectors.toList()));

        Map<String, Integer> distinctDoctorsByPatient = visits.stream()
                .collect(Collectors.groupingBy(Visit::patientId,
                        Collectors.flatMapping(visit -> visit.doctorIds().stream(),
                                Collectors.collectingAndThen(Collectors.toSet(), doctors -> doctors.size()))));

        return byPatient.entrySet().stream()
                .map(entry -> summary(entry.getKey(), entry.getValue(), distinctDoctorsByPatient.get(entry.getKey())))
                .toList();
    }

    private static PatientSummary summary(String patientId, List<Visit> visits, int distinctDoctors) {
        List<String> doctors = visits.stream()
                .flatMap(visit -> visit.doctorIds().stream())
                .sorted()
                .toList();
        return new PatientSummary(patientId, visits.size(), doctors, distinctDoctors);
    }
}
