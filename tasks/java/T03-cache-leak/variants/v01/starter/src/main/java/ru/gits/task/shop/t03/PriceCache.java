package ru.gits.task.shop.t03;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Caches product prices loaded from the pricing service. Used by a single storefront thread.
 */
public final class PriceCache {

    private final PriceSource source;
    private final int capacity;
    private final Map<String, Long> prices = new HashMap<>();

    /**
     * @param source   pricing service
     * @param capacity maximum number of prices kept in memory
     */
    public PriceCache(PriceSource source, int capacity) {
        this.source = Objects.requireNonNull(source, "source");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Price of a product; loaded from the pricing service only when it is not cached. */
    public long price(String sku) {
        Objects.requireNonNull(sku, "sku");
        Long cached = prices.get(sku);
        if (cached != null) {
            return cached;
        }
        long loaded = source.priceOf(sku);
        prices.put(sku, loaded);
        return loaded;
    }

    /** Forgets a price, e.g. after the pricing service announced a change. */
    public void invalidate(String sku) {
        prices.remove(sku);
    }

    /** Number of cached prices. */
    public int size() {
        return prices.size();
    }

    public int capacity() {
        return capacity;
    }
}
