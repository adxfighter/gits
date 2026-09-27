package ru.gits.task.energy.t05;

import java.util.Objects;

/**
 * A retail electricity customer.
 *
 * @param accountNo account number
 * @param rural     the customer lives in a rural area (30% discount on energy)
 */
public record Customer(String accountNo, boolean rural) {

    public Customer {
        Objects.requireNonNull(accountNo, "accountNo");
    }
}
