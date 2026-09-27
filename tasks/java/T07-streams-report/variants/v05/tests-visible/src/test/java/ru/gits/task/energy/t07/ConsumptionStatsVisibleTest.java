package ru.gits.task.energy.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class ConsumptionStatsVisibleTest {

    private static Reading reading(String meter, int day, long wh) {
        return new Reading(meter, LocalDate.of(2026, 6, day), wh);
    }

    @Test
    void daysWithConsumptionAndExport() {
        List<MeterStats> stats = new ConsumptionStats().build(List.of("M-1"), List.of(
                reading("M-1", 1, 5_000),
                reading("M-1", 2, -1_200),
                reading("M-1", 3, 3_200)));

        assertThat(stats).containsExactly(new MeterStats("M-1", 7_000, 3,
                OptionalLong.of(-1_200), OptionalLong.of(5_000), OptionalDouble.of(7_000 / 3.0)));
    }

    @Test
    void metersAreOrderedAndOthersIgnored() {
        List<MeterStats> stats = new ConsumptionStats().build(List.of("M-2", "M-1"), List.of(
                reading("M-1", 1, -100),
                reading("M-1", 2, 300),
                reading("M-2", 1, -50),
                reading("M-2", 2, 250),
                reading("M-9", 1, 1_000_000)));

        assertThat(stats).extracting(MeterStats::meterId).containsExactly("M-1", "M-2");
        assertThat(stats).extracting(MeterStats::totalWh).containsExactly(200L, 200L);
        assertThat(stats).extracting(MeterStats::days).containsExactly(2, 2);
    }
}
