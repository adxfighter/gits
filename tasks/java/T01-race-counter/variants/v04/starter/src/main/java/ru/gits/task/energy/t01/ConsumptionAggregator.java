package ru.gits.task.energy.t01;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Accumulates consumption per meter. {@link #add(MeterReading)} is called by many threads at once;
 * dispatchers read {@link #snapshot()} and {@link #total()} while readings keep arriving.
 */
public final class ConsumptionAggregator {

    /** A single meter cannot report more than this in one interval (sanity limit, 1 MWh). */
    public static final long MAX_INTERVAL_WATT_HOURS = 1_000_000;

    private final Map<String, Long> wattHoursByMeter = new HashMap<>();

    /**
     * Adds a reading to its meter's total.
     *
     * @throws IllegalArgumentException for a negative or implausibly large reading
     */
    public void add(MeterReading reading) {
        Objects.requireNonNull(reading, "reading");
        long wattHours = reading.wattHours();
        if (wattHours < 0 || wattHours > MAX_INTERVAL_WATT_HOURS) {
            throw new IllegalArgumentException("Implausible reading " + wattHours + " Wh from " + reading.meterId());
        }
        long current = wattHoursByMeter.getOrDefault(reading.meterId(), 0L);
        wattHoursByMeter.put(reading.meterId(), current + wattHours);
    }

    /** Consumption of one meter, 0 for an unknown meter. */
    public long totalFor(String meterId) {
        return wattHoursByMeter.getOrDefault(meterId, 0L);
    }

    /** Consumption of all meters. */
    public long total() {
        long sum = 0;
        for (long value : wattHoursByMeter.values()) {
            sum += value;
        }
        return sum;
    }

    /** Copy of the per-meter totals, safe to hand out to reporting code. */
    public Map<String, Long> snapshot() {
        return Map.copyOf(wattHoursByMeter);
    }

    public int meterCount() {
        return wattHoursByMeter.size();
    }
}
