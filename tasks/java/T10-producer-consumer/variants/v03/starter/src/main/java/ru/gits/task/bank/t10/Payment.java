package ru.gits.task.bank.t10;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A payment passed by the gateway.
 */
public record Payment(String id, String account, BigDecimal amount) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
    }
}
