package ru.gits.task.logistics.t08;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

/**
 * Delivery price offered by a carrier.
 */
public record CarrierRate(String carrier, BigDecimal price) {

    /** Cheapest first, equal prices by carrier name. */
    public static final Comparator<CarrierRate> CHEAPEST_FIRST =
            Comparator.comparing(CarrierRate::price).thenComparing(CarrierRate::carrier);

    public CarrierRate {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(price, "price");
    }
}
