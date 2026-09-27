package ru.gits.task.energy.t05;

import java.util.Objects;

/**
 * A retail electricity customer.
 *
 * @param accountNo     account number
 * @param plan          tariff plan
 * @param rural         the customer lives in a rural area
 * @param electricStove the dwelling is equipped with an electric stove
 */
public record Customer(String accountNo, TariffPlan plan, boolean rural, boolean electricStove) {

    public Customer {
        Objects.requireNonNull(accountNo, "accountNo");
        Objects.requireNonNull(plan, "plan");
    }
}
