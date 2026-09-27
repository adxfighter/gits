package ru.gits.task.shop.t08;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Best purchase price from two suppliers.
 */
public final class PriceAggregator {

    private final SupplierClient first;
    private final SupplierClient second;

    public PriceAggregator(SupplierClient first, SupplierClient second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "second");
    }

    /**
     * Requests both suppliers and combines their answers. Completes when both suppliers answered or failed.
     */
    public CompletableFuture<PriceOffer> offer(String sku) {
        CompletableFuture<BigDecimal> firstQuote = first.quote(sku);
        CompletableFuture<BigDecimal> secondQuote = second.quote(sku);

        return firstQuote.thenCombine(secondQuote, (firstPrice, secondPrice) -> new PriceOffer(
                sku,
                Map.of(first.name(), firstPrice, second.name(), secondPrice),
                Optional.of(firstPrice.min(secondPrice))));
    }
}
