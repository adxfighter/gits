package ru.gits.task.shop.t08;

import java.math.BigDecimal;
import java.util.HashMap;
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
        CompletableFuture<Optional<BigDecimal>> firstQuote = safeQuote(first, sku);
        CompletableFuture<Optional<BigDecimal>> secondQuote = safeQuote(second, sku);

        return firstQuote.thenCombine(secondQuote, (firstPrice, secondPrice) -> {
            Map<String, BigDecimal> quotes = new HashMap<>();
            firstPrice.ifPresent(price -> quotes.put(first.name(), price));
            secondPrice.ifPresent(price -> quotes.put(second.name(), price));
            Optional<BigDecimal> best = quotes.values().stream().min(BigDecimal::compareTo);
            return new PriceOffer(sku, quotes, best);
        });
    }

    /** A supplier failure, asynchronous or thrown right away, becomes an empty answer of that supplier. */
    private static CompletableFuture<Optional<BigDecimal>> safeQuote(SupplierClient supplier, String sku) {
        try {
            return supplier.quote(sku)
                    .thenApply(Optional::of)
                    .exceptionally(failure -> Optional.empty());
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
