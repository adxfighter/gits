package ru.gits.task.bank.t03;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded cache of exchange rates with hit/miss statistics. Used by a single conversion thread.
 */
public final class ExchangeRateCache {

    private static final MathContext PRECISION = new MathContext(12, RoundingMode.HALF_EVEN);

    private final RateProvider provider;
    private final int capacity;
    private final Map<CurrencyPair, BigDecimal> rates;
    private long hits;
    private long misses;

    public ExchangeRateCache(RateProvider provider, int capacity) {
        this.provider = Objects.requireNonNull(provider, "provider");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        // access order: a lookup makes the entry the most recent one, so hot pairs are not evicted
        this.rates = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CurrencyPair, BigDecimal> eldest) {
                return size() > ExchangeRateCache.this.capacity;
            }
        };
    }

    /** Rate of a pair, requested from the provider only on a cache miss. */
    public BigDecimal rate(CurrencyPair pair) {
        Objects.requireNonNull(pair, "pair");
        BigDecimal cached = rates.get(pair);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        BigDecimal loaded = provider.rate(pair);
        rates.put(pair, loaded);
        return loaded;
    }

    /** Inverse rate (e.g. RUB/USD from USD/RUB) without an extra provider request. */
    public BigDecimal inverseRate(CurrencyPair pair) {
        return BigDecimal.ONE.divide(rate(pair.inverse()), PRECISION);
    }

    /** Converts an amount of the base currency into the quote currency, rounded to 2 decimals. */
    public BigDecimal convert(BigDecimal amount, CurrencyPair pair) {
        Objects.requireNonNull(amount, "amount");
        return amount.multiply(rate(pair)).setScale(2, RoundingMode.HALF_EVEN);
    }

    public long hits() {
        return hits;
    }

    public long misses() {
        return misses;
    }

    public int size() {
        return rates.size();
    }
}
