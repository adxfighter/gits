package ru.gits.task.energy.t07;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * Daily consumption statistics for the customer app.
 */
public final class ConsumptionStats {

    /**
     * Builds statistics for every meter of the list, ordered by meter id.
     */
    public List<MeterStats> build(List<String> meterIds, List<Reading> readings) {
        Map<String, List<Reading>> byMeter = readings.stream()
                .collect(Collectors.groupingBy(Reading::meterId));

        return meterIds.stream()
                .sorted()
                .map(meterId -> stats(meterId, byMeter.getOrDefault(meterId, List.of())))
                .toList();
    }

    private static MeterStats stats(String meterId, List<Reading> readings) {
        long total = readings.stream()
                .mapToLong(Reading::wh)
                .sum();
        long days = readings.stream()
                .map(Reading::day)
                .distinct()
                .count();
        long min = readings.stream()
                .map(Reading::wh)
                .reduce(0L, Math::min);
        long max = readings.stream()
                .map(Reading::wh)
                .reduce(0L, Math::max);
        double average = (double) total / days;
        return new MeterStats(meterId, total, (int) days,
                OptionalLong.of(min), OptionalLong.of(max), OptionalDouble.of(average));
    }
}
