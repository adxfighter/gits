package ru.gits.task.logistics.t02;

import java.util.Objects;

/**
 * Moves goods between warehouses. Used concurrently by the order-processing pool and the nightly
 * rebalancing job.
 */
public final class StockMovementService {

    /**
     * Moves a batch of goods atomically.
     *
     * @throws IllegalArgumentException for a non-positive quantity or a move within one warehouse
     * @throws IllegalStateException    when the source warehouse does not have enough goods (nothing changes)
     */
    public void move(Warehouse from, Warehouse to, String sku, int quantity) {
        requireDifferent(from, to);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        synchronized (from) {
            synchronized (to) {
                from.take(sku, quantity);
                to.put(sku, quantity);
            }
        }
    }

    /**
     * Evens out the stock of a product between two warehouses: half of the difference (rounded down)
     * moves to the warehouse that has less.
     *
     * @return how many items were moved, 0 when the stock is already even
     */
    public int rebalance(Warehouse a, Warehouse b, String sku) {
        requireDifferent(a, b);
        synchronized (a) {
            synchronized (b) {
                int difference = a.quantity(sku) - b.quantity(sku);
                int toMove = Math.abs(difference) / 2;
                if (toMove == 0) {
                    return 0;
                }
                if (difference > 0) {
                    a.take(sku, toMove);
                    b.put(sku, toMove);
                } else {
                    b.take(sku, toMove);
                    a.put(sku, toMove);
                }
                return toMove;
            }
        }
    }

    private static void requireDifferent(Warehouse first, Warehouse second) {
        Objects.requireNonNull(first, "first warehouse");
        Objects.requireNonNull(second, "second warehouse");
        if (first == second || first.code().equals(second.code())) {
            throw new IllegalArgumentException("Source and destination are the same warehouse " + first.code());
        }
    }
}
