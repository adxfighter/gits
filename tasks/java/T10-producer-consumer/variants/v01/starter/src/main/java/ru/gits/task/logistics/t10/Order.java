package ru.gits.task.logistics.t10;

import java.util.Objects;

/**
 * A warehouse order.
 */
public record Order(String id, String sku, int quantity) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sku, "sku");
    }
}
