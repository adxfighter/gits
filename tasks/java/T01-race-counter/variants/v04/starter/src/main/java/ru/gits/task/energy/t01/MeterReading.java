package ru.gits.task.energy.t01;

import java.util.Objects;

/**
 * Consumption increment reported by a smart meter for one interval.
 *
 * @param meterId      meter serial number
 * @param wattHours    consumption during the interval, not negative
 * @param intervalNo   sequence number of the interval reported by the meter
 */
public record MeterReading(String meterId, long wattHours, long intervalNo) {

    public MeterReading {
        Objects.requireNonNull(meterId, "meterId");
        if (meterId.isBlank()) {
            throw new IllegalArgumentException("meterId must not be blank");
        }
    }
}
