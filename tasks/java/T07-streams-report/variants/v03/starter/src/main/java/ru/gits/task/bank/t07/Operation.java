package ru.gits.task.bank.t07;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An account operation: positive amount is a credit, negative amount is a debit.
 */
public record Operation(String id, LocalDate date, BigDecimal amount) {

    public Operation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
    }

    public boolean isDebit() {
        return amount.signum() < 0;
    }
}
