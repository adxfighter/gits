package ru.gits.task.energy.t07;

import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Daily consumption statistics of one meter. Daily values are sums of the day's readings.
 * Minimum, maximum and average are empty when the meter has no readings.
 */
public record MeterStats(
        String meterId,
        long totalWh,
        int days,
        OptionalLong minDailyWh,
        OptionalLong maxDailyWh,
        OptionalDouble averageDailyWh) {
}
