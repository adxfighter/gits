package ru.gits.task.energy.t07;

import java.time.LocalDate;
import java.util.List;
import java.util.LongSummaryStatistics;
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
        Map<LocalDate, Long> daily = readings.stream()
                .collect(Collectors.groupingBy(Reading::day, Collectors.summingLong(Reading::wh)));
        LongSummaryStatistics statistics = daily.values().stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();
        if (statistics.getCount() == 0) {
            return new MeterStats(meterId, 0, 0, OptionalLong.empty(), OptionalLong.empty(), OptionalDouble.empty());
        }
        return new MeterStats(meterId, statistics.getSum(), (int) statistics.getCount(),
                OptionalLong.of(statistics.getMin()), OptionalLong.of(statistics.getMax()),
                OptionalDouble.of(statistics.getAverage()));
    }
}
