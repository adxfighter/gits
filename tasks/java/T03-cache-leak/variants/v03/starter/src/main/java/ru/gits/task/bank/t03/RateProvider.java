package ru.gits.task.bank.t03;

import java.math.BigDecimal;

/**
 * External paid provider of exchange rates. Every call costs money.
 */
@FunctionalInterface
public interface RateProvider {

    /** How many units of the quote currency one unit of the base currency costs. */
    BigDecimal rate(CurrencyPair pair);
}
