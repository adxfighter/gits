package ru.gits.task.bank.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExchangeRateCacheHiddenTest {

    private static final CurrencyPair USD_RUB = CurrencyPair.of("USD/RUB");
    private static final CurrencyPair EUR_RUB = CurrencyPair.of("EUR/RUB");

    private final List<CurrencyPair> requests = new ArrayList<>();
    private final RateProvider provider = pair -> {
        requests.add(pair);
        return new BigDecimal("2.00");
    };

    @Test
    void popularPairsSurviveRequestsForRareCurrencies() {
        var cache = new ExchangeRateCache(provider, 10);
        cache.rate(USD_RUB);
        cache.rate(EUR_RUB);

        List<String> rare = List.of("AMD", "GEL", "KZT", "UZS", "TRY", "AED", "THB", "VND", "INR", "BRL", "ZAR");
        for (String code : rare) {
            cache.rate(new CurrencyPair(code, "RUB"));
            cache.rate(USD_RUB);
            cache.rate(EUR_RUB);
        }
        requests.clear();
        cache.rate(USD_RUB);
        cache.rate(EUR_RUB);

        assertThat(requests).isEmpty();
    }

    @Test
    void leastRecentlyUsedPairIsEvicted() {
        var cache = new ExchangeRateCache(provider, 2);
        CurrencyPair gbp = CurrencyPair.of("GBP/RUB");

        cache.rate(USD_RUB);
        cache.rate(EUR_RUB);
        cache.rate(USD_RUB);
        cache.rate(gbp);  // EUR/RUB has not been used the longest
        requests.clear();

        cache.rate(USD_RUB);
        cache.rate(gbp);
        cache.rate(EUR_RUB);

        assertThat(requests).containsExactly(EUR_RUB);
    }

    @Test
    void statisticsReflectHitsAfterEvictionPolicyChange() {
        var cache = new ExchangeRateCache(provider, 3);

        for (int round = 0; round < 100; round++) {
            cache.rate(USD_RUB);
            cache.rate(new CurrencyPair("C" + (char) ('A' + round % 26) + (char) ('A' + round / 26), "RUB"));
        }

        // USD/RUB: 1 miss + 99 hits; each rare pair is a miss
        assertThat(cache.hits()).isEqualTo(99);
        assertThat(cache.misses()).isEqualTo(101);
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    void inverseRateUsesTheCachedDirectRate() {
        var cache = new ExchangeRateCache(provider, 5);
        CurrencyPair rubUsd = USD_RUB.inverse();

        cache.rate(rubUsd);
        requests.clear();

        assertThat(cache.inverseRate(USD_RUB)).isEqualByComparingTo("0.5");
        assertThat(requests).isEmpty();
    }

    @Test
    void sizeStaysBounded() {
        var cache = new ExchangeRateCache(provider, 4);

        for (int i = 0; i < 26 * 26; i++) {
            cache.rate(new CurrencyPair("X" + (char) ('A' + i % 26) + (char) ('A' + i / 26), "RUB"));
        }

        assertThat(cache.size()).isEqualTo(4);
        assertThat(cache.misses()).isEqualTo(26 * 26);
    }
}
