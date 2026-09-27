package ru.gits.task.bank.t06;

import java.util.Objects;

/**
 * A foreign currency account: the same requisites plus ISO currency code.
 */
public class CurrencyAccount extends Account {

    private final String currency;

    public CurrencyAccount(String bik, String number, String currency) {
        super(bik, number);
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CurrencyAccount that)) {
            return false;
        }
        return super.equals(other) && currency.equals(that.currency);
    }

    @Override
    public String toString() {
        return super.toString() + " " + currency;
    }
}
