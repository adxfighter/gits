package ru.gits.task.bank.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyStatementHiddenTest {

    private static Operation op(String id, String date, String amount) {
        return new Operation(id, LocalDate.parse(date), new BigDecimal(amount));
    }

    @Test
    void monthsAreChronologicalAcrossTheYearBoundary() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2025-11-03", "-1"),
                op("2", "2025-12-03", "-2"),
                op("3", "2026-01-03", "-3"),
                op("4", "2026-02-03", "-4")),
                YearMonth.of(2025, 11), YearMonth.of(2026, 2));

        assertThat(lines).extracting(MonthLine::month).containsExactly(
                YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    void monthWithoutOperationsHasAnEmptyLine() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2026-06-10", "-500")),
                YearMonth.of(2026, 6), YearMonth.of(2026, 8));

        assertThat(lines).hasSize(3);
        MonthLine july = lines.get(1);
        assertThat(july.month()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(july.operationCount()).isZero();
        assertThat(july.turnover()).isEqualByComparingTo("0");
        assertThat(july.largestDebit()).isEmpty();
    }

    @Test
    void monthWithCreditsOnlyHasNoLargestDebit() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2026-07-05", "80000"),
                op("2", "2026-07-20", "1500.25")),
                YearMonth.of(2026, 7), YearMonth.of(2026, 7));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).operationCount()).isEqualTo(2);
        assertThat(lines.get(0).turnover()).isEqualByComparingTo("81500.25");
        assertThat(lines.get(0).largestDebit()).isEmpty();
    }

    @Test
    void emptyHistoryGivesALinePerMonth() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(),
                YearMonth.of(2025, 12), YearMonth.of(2026, 1));

        assertThat(lines).extracting(MonthLine::month).containsExactly(YearMonth.of(2025, 12), YearMonth.of(2026, 1));
        assertThat(lines).allSatisfy(line -> assertThat(line.largestDebit()).isEmpty());
    }

    @Test
    void largestDebitIsTheBiggestByAbsoluteValue() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2026-09-01", "-10.00"),
                op("2", "2026-09-02", "-2500.10"),
                op("3", "2026-09-03", "99999.99"),
                op("4", "2026-09-30", "-700")),
                YearMonth.of(2026, 9), YearMonth.of(2026, 9));

        assertThat(lines.get(0).largestDebit()).get().satisfies(debit -> assertThat(debit).isEqualByComparingTo("2500.10"));
    }

    @Test
    void periodBoundariesAreInclusiveAcrossYears() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2025-10-31", "-1"),
                op("2", "2025-11-01", "-2"),
                op("3", "2026-01-31", "-3"),
                op("4", "2026-02-01", "-4")),
                YearMonth.of(2025, 11), YearMonth.of(2026, 1));

        assertThat(lines).extracting(MonthLine::operationCount).containsExactly(1, 0, 1);
    }
}
