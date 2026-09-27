package ru.gits.task.energy.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class ConsumptionStatsHiddenTest {

    private static Reading reading(String meter, int day, long wh) {
        return new Reading(meter, LocalDate.of(2026, 6, day), wh);
    }

    private static MeterStats single(List<Reading> readings) {
        List<MeterStats> stats = new ConsumptionStats().build(List.of("M-1"), readings);
        assertThat(stats).hasSize(1);
        return stats.get(0);
    }

    @Test
    void minimumOfOrdinaryConsumerIsTheLowestDay() {
        MeterStats stats = single(List.of(
                reading("M-1", 1, 8_000),
                reading("M-1", 2, 6_500),
                reading("M-1", 3, 9_100)));

        assertThat(stats.minDailyWh()).isEqualTo(OptionalLong.of(6_500));
        assertThat(stats.maxDailyWh()).isEqualTo(OptionalLong.of(9_100));
    }

    @Test
    void maximumOfPermanentExporterIsNegative() {
        MeterStats stats = single(List.of(
                reading("M-1", 1, -4_000),
                reading("M-1", 2, -2_500),
                reading("M-1", 3, -7_000)));

        assertThat(stats.maxDailyWh()).isEqualTo(OptionalLong.of(-2_500));
        assertThat(stats.minDailyWh()).isEqualTo(OptionalLong.of(-7_000));
        assertThat(stats.averageDailyWh()).isEqualTo(OptionalDouble.of(-4_500.0));
    }

    @Test
    void minAndMaxAreTakenOverDailySums() {
        List<Reading> readings = new ArrayList<>();
        // day 1: night consumption, day export, evening consumption = 2 000
        readings.add(reading("M-1", 1, 3_000));
        readings.add(reading("M-1", 1, -4_000));
        readings.add(reading("M-1", 1, 3_000));
        // day 2: four intervals of 1 500 = 6 000
        for (int i = 0; i < 4; i++) {
            readings.add(reading("M-1", 2, 1_500));
        }

        MeterStats stats = single(readings);

        assertThat(stats).isEqualTo(new MeterStats("M-1", 8_000, 2,
                OptionalLong.of(2_000), OptionalLong.of(6_000), OptionalDouble.of(4_000.0)));
    }

    @Test
    void meterWithoutReadingsHasEmptyStatistics() {
        List<MeterStats> stats = new ConsumptionStats().build(List.of("M-new", "M-1"), List.of(
                reading("M-1", 1, 1_000)));

        assertThat(stats).containsExactly(
                new MeterStats("M-1", 1_000, 1, OptionalLong.of(1_000), OptionalLong.of(1_000), OptionalDouble.of(1_000.0)),
                new MeterStats("M-new", 0, 0, OptionalLong.empty(), OptionalLong.empty(), OptionalDouble.empty()));
    }

    @Test
    void noReadingsAtAll() {
        assertThat(new ConsumptionStats().build(List.of("M-1"), List.of()))
                .containsExactly(new MeterStats("M-1", 0, 0, OptionalLong.empty(), OptionalLong.empty(), OptionalDouble.empty()));
    }

    @Test
    void dayWithZeroNetConsumptionIsStillADay() {
        MeterStats stats = single(List.of(
                reading("M-1", 1, 2_000),
                reading("M-1", 1, -2_000),
                reading("M-1", 2, 5_000)));

        assertThat(stats.days()).isEqualTo(2);
        assertThat(stats.minDailyWh()).isEqualTo(OptionalLong.of(0));
        assertThat(stats.averageDailyWh()).isEqualTo(OptionalDouble.of(2_500.0));
    }
}
