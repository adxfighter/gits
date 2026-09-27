package ru.gits.task.logistics.t08;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Remote tariff API of a carrier.
 */
public interface CarrierClient {

    String name();

    /** Asynchronously requests the delivery price of a parcel of the given weight. */
    CompletableFuture<BigDecimal> rate(int weightGrams);
}
