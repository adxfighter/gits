package ru.gits.task.bank.t02;

import java.util.Objects;

/**
 * Moves money between accounts. Called by many request-processing threads at the same time.
 */
public final class TransferService {

    /**
     * Transfers money atomically: either both the debit and the credit happen, or neither does.
     *
     * @throws IllegalArgumentException for a non-positive amount or a transfer to the same account
     * @throws IllegalStateException    when the source account has insufficient funds (nothing changes)
     */
    public void transfer(Account from, Account to, long amountKopecks) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amountKopecks <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amountKopecks);
        }
        if (from == to || from.id() == to.id()) {
            throw new IllegalArgumentException("Cannot transfer to the same account " + from.id());
        }
        synchronized (from) {
            synchronized (to) {
                from.debit(amountKopecks);
                to.credit(amountKopecks);
            }
        }
    }
}
