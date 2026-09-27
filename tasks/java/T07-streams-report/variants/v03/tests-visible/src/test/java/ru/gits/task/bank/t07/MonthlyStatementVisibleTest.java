package ru.gits.task.bank.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyStatementVisibleTest {

    private static Operation op(String id, String date, String amount) {
        return new Operation(id, LocalDate.parse(date), new BigDecimal(amount));
    }

    @Test
    void monthsWithDebitsInOneYear() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2026-03-05", "50000.00"),
                op("2", "2026-03-10", "-1200.50"),
                op("3", "2026-03-28", "-300.00"),
                op("4", "2026-04-02", "-99.90")),
                YearMonth.of(2026, 3), YearMonth.of(2026, 4));

        assertThat(lines).extracting(MonthLine::month).containsExactly(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        MonthLine march = lines.get(0);
        assertThat(march.operationCount()).isEqualTo(3);
        assertThat(march.turnover()).isEqualByComparingTo("51500.50");
        assertThat(march.largestDebit()).get().satisfies(debit -> assertThat(debit).isEqualByComparingTo("1200.50"));
    }

    @Test
    void operationsOutsideThePeriodAreIgnored() {
        List<MonthLine> lines = new MonthlyStatement().build(List.of(
                op("1", "2026-02-28", "-10"),
                op("2", "2026-03-01", "-20"),
                op("3", "2026-04-01", "-30")),
                YearMonth.of(2026, 3), YearMonth.of(2026, 3));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).operationCount()).isEqualTo(1);
        assertThat(lines.get(0).turnover()).isEqualByComparingTo("20");
    }
}
