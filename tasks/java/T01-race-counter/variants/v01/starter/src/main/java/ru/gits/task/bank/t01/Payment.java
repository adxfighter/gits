package ru.gits.task.bank.t01;

import java.util.Objects;

/**
 * A payment accepted by the processing centre.
 *
 * @param id             payment identifier from the bank core
 * @param amountKopecks  payment amount in kopecks; must be positive to be counted
 * @param payerAccount   payer account number
 */
public record Payment(String id, long amountKopecks, String payerAccount) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payerAccount, "payerAccount");
    }

    public boolean hasPositiveAmount() {
        return amountKopecks > 0;
    }
}
