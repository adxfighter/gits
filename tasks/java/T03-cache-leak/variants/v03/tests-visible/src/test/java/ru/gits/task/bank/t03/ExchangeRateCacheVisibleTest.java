package ru.gits.task.bank.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ExchangeRateCacheVisibleTest {

    private static final CurrencyPair USD_RUB = CurrencyPair.of("USD/RUB");

    @Test
    void repeatedRequestIsAHit() {
        var cache = new ExchangeRateCache(pair -> new BigDecimal("92.50"), 10);

        cache.rate(USD_RUB);
        cache.rate(USD_RUB);

        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
    }

    @Test
    void convertsAnAmount() {
        var cache = new ExchangeRateCache(pair -> new BigDecimal("92.50"), 10);

        assertThat(cache.convert(new BigDecimal("100"), USD_RUB)).isEqualByComparingTo("9250.00");
    }
}
