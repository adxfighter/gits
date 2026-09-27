package ru.gits.task.shop.t07;

import java.util.Objects;

/**
 * Units of one product sold in one order.
 */
public record Sale(String orderId, String sku, int units) {

    public Sale {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive: " + units);
        }
    }
}
