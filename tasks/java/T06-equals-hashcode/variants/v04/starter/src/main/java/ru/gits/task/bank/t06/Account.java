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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account that)) {
            return false;
        }
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
