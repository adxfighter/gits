package ru.gits.task.telecom.t02;

import java.util.Objects;

/**
 * Settlement account of a telecom operator (or of the clearing house). Locking rule: the balance changes
 * only while holding this object's monitor; {@link #withdraw(long)} and {@link #deposit(long)} expect the
 * caller to hold it.
 */
public final class OperatorAccount {

    private final int operatorCode;
    private final String name;
    private long balanceKopecks;

    public OperatorAccount(int operatorCode, String name, long initialBalanceKopecks) {
        this.operatorCode = operatorCode;
        this.name = Objects.requireNonNull(name, "name");
        this.balanceKopecks = initialBalanceKopecks;
    }

    /** Unique operator code; the clearing house has the largest code in the system. */
    public int operatorCode() {
        return operatorCode;
    }

    public String name() {
        return name;
    }

    public synchronized long balance() {
        return balanceKopecks;
    }

    /** Caller must hold this account's monitor. */
    void withdraw(long amountKopecks) {
        if (amountKopecks > balanceKopecks) {
            throw new IllegalStateException(name + " cannot pay " + amountKopecks);
        }
        balanceKopecks -= amountKopecks;
    }

    /** Caller must hold this account's monitor. */
    void deposit(long amountKopecks) {
        balanceKopecks += amountKopecks;
    }
}
