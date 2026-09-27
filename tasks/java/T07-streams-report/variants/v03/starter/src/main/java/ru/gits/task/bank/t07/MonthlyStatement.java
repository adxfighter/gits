package ru.gits.task.bank.t07;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monthly account statement.
 */
public final class MonthlyStatement {

    /**
     * Builds one line per month from {@code from} to {@code to} inclusive, in chronological order.
     */
    public List<MonthLine> build(List<Operation> operations, YearMonth from, YearMonth to) {
        Map<YearMonth, List<Operation>> byMonth = operations.stream()
                .filter(op -> inPeriod(YearMonth.from(op.date()), from, to))
                .collect(Collectors.groupingBy(op -> YearMonth.from(op.date())));

        return Stream.iterate(from, month -> !month.isAfter(to), month -> month.plusMonths(1))
                .sorted(Comparator.comparing(YearMonth::getMonth))
                .map(month -> line(month, byMonth.getOrDefault(month, List.of())))
                .toList();
    }

    private static MonthLine line(YearMonth month, List<Operation> operations) {
        BigDecimal turnover = operations.stream()
                .map(op -> op.amount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal largestDebit = operations.stream()
                .filter(Operation::isDebit)
                .map(op -> op.amount().abs())
                .max(Comparator.naturalOrder())
                .get();
        return new MonthLine(month, operations.size(), turnover, Optional.of(largestDebit));
    }

    private static boolean inPeriod(YearMonth month, YearMonth from, YearMonth to) {
        return !month.isBefore(from) && !month.isAfter(to);
    }
}
