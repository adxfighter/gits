package ru.gits.task.bank.t07;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

/**
 * One month of the statement.
 *
 * @param turnover     sum of absolute amounts of all operations of the month
 * @param largestDebit absolute amount of the largest debit, empty when there were no debits
 */
public record MonthLine(YearMonth month, int operationCount, BigDecimal turnover, Optional<BigDecimal> largestDebit) {
}
