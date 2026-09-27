package ru.gits.task.medicine.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class VisitReportHiddenTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Test
    void repeatedVisitsToOneDoctorAreCountedOnce() {
        List<Visit> visits = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            visits.add(new Visit("v" + day, "P-1", LocalDate.of(2026, 3, day), List.of("D-physio")));
        }

        assertThat(new VisitReport().summarize(visits, FROM, TO))
                .containsExactly(new PatientSummary("P-1", 10, List.of("D-physio"), 1));
    }

    @Test
    void doctorRecordedTwiceInOneVisitIsCountedOnce() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 3, 3), List.of("D-2", "D-1", "D-2"))), FROM, TO);

        assertThat(report).containsExactly(new PatientSummary("P-1", 1, List.of("D-1", "D-2"), 2));
    }

    @Test
    void lastDayOfThePeriodIsIncluded() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 3, 31), List.of("D-1")),
                new Visit("v2", "P-2", LocalDate.of(2026, 4, 1), List.of("D-1"))), FROM, TO);

        assertThat(report).containsExactly(new PatientSummary("P-1", 1, List.of("D-1"), 1));
    }

    @Test
    void visitWithoutDoctorsCountsAsAVisit() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 3, 3), List.of()),
                new Visit("v2", "P-1", LocalDate.of(2026, 3, 4), List.of("D-1")),
                new Visit("v3", "P-2", LocalDate.of(2026, 3, 5), List.of())), FROM, TO);

        assertThat(report).containsExactly(
                new PatientSummary("P-1", 2, List.of("D-1"), 1),
                new PatientSummary("P-2", 1, List.of(), 0));
    }

    @Test
    void mixedHistoryOfOnePatient() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-7", LocalDate.of(2026, 3, 2), List.of("D-therapist")),
                new Visit("v2", "P-7", LocalDate.of(2026, 3, 9), List.of("D-surgeon", "D-therapist")),
                new Visit("v3", "P-7", LocalDate.of(2026, 3, 16), List.of("D-anesthetist", "D-surgeon")),
                new Visit("v4", "P-7", LocalDate.of(2026, 3, 31), List.of("D-surgeon"))), FROM, TO);

        assertThat(report).containsExactly(
                new PatientSummary("P-7", 4, List.of("D-anesthetist", "D-surgeon", "D-therapist"), 3));
    }

    @Test
    void doctorsOutsideThePeriodAreNotCounted() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 1, 15), List.of("D-cardio", "D-neuro")),
                new Visit("v2", "P-1", LocalDate.of(2026, 2, 28), List.of("D-surgeon")),
                new Visit("v3", "P-1", LocalDate.of(2026, 3, 10), List.of("D-therapist")),
                new Visit("v4", "P-1", LocalDate.of(2026, 4, 2), List.of("D-oculist")),
                new Visit("v5", "P-2", LocalDate.of(2026, 3, 11), List.of("D-therapist"))), FROM, TO);

        assertThat(report).containsExactly(
                new PatientSummary("P-1", 1, List.of("D-therapist"), 1),
                new PatientSummary("P-2", 1, List.of("D-therapist"), 1));
    }

    @Test
    void noVisitsInThePeriodGiveAnEmptyReport() {
        assertThat(new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 2, 1), List.of("D-1"))), FROM, TO)).isEmpty();
    }
}
