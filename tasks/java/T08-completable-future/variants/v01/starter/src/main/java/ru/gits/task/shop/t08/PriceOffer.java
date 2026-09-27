package ru.gits.task.shop.t08;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Prices of the suppliers that answered, by supplier name, and the best (lowest) of them.
 */
public record PriceOffer(String sku, Map<String, BigDecimal> quotes, Optional<BigDecimal> bestPrice) {

    public PriceOffer {
        Objects.requireNonNull(sku, "sku");
        quotes = Map.copyOf(quotes);
        Objects.requireNonNull(bestPrice, "bestPrice");
    }
}
