package ru.gits.task.bank.t02;

/**
 * A client account. Locking rule: the balance is changed only while holding this object's monitor
 * ({@code synchronized (account)}); {@link #debit(long)} and {@link #credit(long)} expect the caller to hold it.
 */
public final class Account {

    private final long id;
    private long balanceKopecks;

    public Account(long id, long initialBalanceKopecks) {
        if (initialBalanceKopecks < 0) {
            throw new IllegalArgumentException("Initial balance must not be negative");
        }
        this.id = id;
        this.balanceKopecks = initialBalanceKopecks;
    }

    public long id() {
        return id;
    }

    public synchronized long balance() {
        return balanceKopecks;
    }

    /** Caller must hold this account's monitor. */
    void debit(long amountKopecks) {
        if (amountKopecks > balanceKopecks) {
            throw new IllegalStateException("Insufficient funds on account " + id);
        }
        balanceKopecks -= amountKopecks;
    }

    /** Caller must hold this account's monitor. */
    void credit(long amountKopecks) {
        balanceKopecks += amountKopecks;
    }
}
