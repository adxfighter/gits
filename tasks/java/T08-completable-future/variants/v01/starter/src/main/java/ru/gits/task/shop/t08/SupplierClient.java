package ru.gits.task.shop.t08;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Remote price API of a supplier.
 */
public interface SupplierClient {

    String name();

    /** Asynchronously requests the purchase price of the product. */
    CompletableFuture<BigDecimal> quote(String sku);
}
