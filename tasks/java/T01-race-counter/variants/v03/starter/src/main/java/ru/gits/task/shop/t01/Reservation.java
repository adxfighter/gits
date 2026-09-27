package ru.gits.task.shop.t01;

import java.util.Objects;

/**
 * Goods reserved for an order.
 *
 * @param orderId  order the goods are reserved for
 * @param sku      stock keeping unit
 * @param quantity reserved quantity, positive
 */
public record Reservation(String orderId, String sku, int quantity) {

    public Reservation {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sku, "sku");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
    }
}
