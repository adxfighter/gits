package ru.gits.task.shop.t05;

import java.util.List;
import java.util.Objects;

/**
 * A customer order.
 *
 * @param lines     ordered products
 * @param promoCode promo code entered by the customer, may be null
 * @param delivery  how the order is delivered
 */
public record Order(List<Line> lines, String promoCode, Delivery delivery) {

    public enum Delivery {
        COURIER,
        PICKUP
    }

    /**
     * @param sku          product
     * @param priceKopecks price of one unit
     * @param quantity     number of units, positive
     */
    public record Line(String sku, long priceKopecks, int quantity) {
        public Line {
            Objects.requireNonNull(sku, "sku");
            if (priceKopecks < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid order line " + sku);
            }
        }
    }

    public Order {
        lines = List.copyOf(lines);
        Objects.requireNonNull(delivery, "delivery");
    }
}
