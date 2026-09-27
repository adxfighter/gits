package ru.gits.task.shop.t06;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shopping cart: SKU -> quantity.
 */
public final class Cart {

    private final Map<Sku, Integer> lines = new LinkedHashMap<>();

    public void add(Sku sku, int quantity) {
        Objects.requireNonNull(sku, "sku");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        lines.merge(sku, quantity, Integer::sum);
    }

    public boolean remove(Sku sku) {
        return lines.remove(sku) != null;
    }

    public int quantityOf(Sku sku) {
        return lines.getOrDefault(sku, 0);
    }

    /** Number of distinct cart lines. */
    public int lineCount() {
        return lines.size();
    }
}
