package ru.gits.task.medicine.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class VisitReportVisibleTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Test
    void patientsSeenByDifferentDoctors() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-2", LocalDate.of(2026, 3, 4), List.of("D-therapist")),
                new Visit("v2", "P-1", LocalDate.of(2026, 3, 5), List.of("D-surgeon", "D-anesthetist")),
                new Visit("v3", "P-2", LocalDate.of(2026, 3, 12), List.of("D-cardio"))), FROM, TO);

        assertThat(report).containsExactly(
                new PatientSummary("P-1", 1, List.of("D-anesthetist", "D-surgeon"), 2),
                new PatientSummary("P-2", 2, List.of("D-cardio", "D-therapist"), 2));
    }

    @Test
    void visitsBeforeThePeriodAreIgnored() {
        List<PatientSummary> report = new VisitReport().summarize(List.of(
                new Visit("v1", "P-1", LocalDate.of(2026, 2, 28), List.of("D-1")),
                new Visit("v2", "P-2", LocalDate.of(2026, 3, 1), List.of("D-2"))), FROM, TO);

        assertThat(report).containsExactly(new PatientSummary("P-2", 1, List.of("D-2"), 1));
    }
}
