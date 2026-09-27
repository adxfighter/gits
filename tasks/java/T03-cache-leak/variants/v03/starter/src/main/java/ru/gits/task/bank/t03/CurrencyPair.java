package ru.gits.task.bank.t03;

import java.util.Objects;

/**
 * A currency pair such as USD/RUB (base/quote, ISO 4217 codes).
 */
public record CurrencyPair(String base, String quote) {

    public CurrencyPair {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(quote, "quote");
        if (!base.matches("[A-Z]{3}") || !quote.matches("[A-Z]{3}") || base.equals(quote)) {
            throw new IllegalArgumentException("Invalid currency pair " + base + "/" + quote);
        }
    }

    public static CurrencyPair of(String code) {
        String[] parts = code.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected BASE/QUOTE: " + code);
        }
        return new CurrencyPair(parts[0], parts[1]);
    }

    public CurrencyPair inverse() {
        return new CurrencyPair(quote, base);
    }
}
