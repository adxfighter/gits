package ru.gits.task.bank.t06;

import java.util.Objects;

/**
 * A ruble account identified by bank BIK and account number.
 */
public class Account {

    private final String bik;
    private final String number;

    public Account(String bik, String number) {
        this.bik = Objects.requireNonNull(bik, "bik");
        this.number = Objects.requireNonNull(number, "number");
    }

    public String bik() {
        return bik;
    }

    public String number() {
        return number;
    }

    /** Accounts of different kinds are never equal, so the class takes part in equality. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Account that = (Account) other;
        return bik.equals(that.bik) && number.equals(that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bik, number);
    }

    @Override
    public String toString() {
        return bik + "/" + number;
    }
}
