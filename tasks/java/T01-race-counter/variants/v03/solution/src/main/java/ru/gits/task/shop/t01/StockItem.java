package ru.gits.task.shop.t01;

import java.util.Objects;
import java.util.Optional;

/**
 * Warehouse stock of one product. Checkout threads call {@link #reserve(String, int)} concurrently,
 * cancellations call {@link #release(Reservation)}.
 */
public final class StockItem {

    private final String sku;
    private int available;

    public StockItem(String sku, int initialStock) {
        this.sku = Objects.requireNonNull(sku, "sku");
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock must not be negative: " + initialStock);
        }
        this.available = initialStock;
    }

    /**
     * Reserves goods for an order.
     *
     * @return the reservation, or empty when there are not enough goods left
     */
    public Optional<Reservation> reserve(String orderId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        synchronized (this) {
            if (available < quantity) {
                return Optional.empty();
            }
            available -= quantity;
        }
        return Optional.of(new Reservation(orderId, sku, quantity));
    }

    /** Returns cancelled goods to the stock. */
    public void release(Reservation reservation) {
        if (!sku.equals(reservation.sku())) {
            throw new IllegalArgumentException("Reservation " + reservation.orderId() + " is for " + reservation.sku());
        }
        synchronized (this) {
            available += reservation.quantity();
        }
    }

    public synchronized int available() {
        return available;
    }

    public String sku() {
        return sku;
    }
}
