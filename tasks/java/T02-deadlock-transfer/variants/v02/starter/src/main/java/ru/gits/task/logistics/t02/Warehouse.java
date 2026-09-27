package ru.gits.task.logistics.t02;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A regional warehouse. Locking rule: stock changes only while holding this object's monitor;
 * {@link #take(String, int)} and {@link #put(String, int)} expect the caller to hold it.
 */
public final class Warehouse {

    private final String code;
    private final Map<String, Integer> stock = new HashMap<>();

    public Warehouse(String code) {
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }

    public synchronized int quantity(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    /** Caller must hold this warehouse's monitor. */
    void take(String sku, int quantity) {
        int available = stock.getOrDefault(sku, 0);
        if (available < quantity) {
            throw new IllegalStateException("Only " + available + " of " + sku + " at " + code);
        }
        stock.put(sku, available - quantity);
    }

    /** Caller must hold this warehouse's monitor. */
    void put(String sku, int quantity) {
        stock.merge(sku, quantity, Integer::sum);
    }

    /** Initial stock loading, before the warehouse is shared between threads. */
    public synchronized void receive(String sku, int quantity) {
        put(sku, quantity);
    }
}
