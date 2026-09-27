package ru.gits.task.energy.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ElectricityBillCalculatorHiddenTest {

    private static final YearMonth JANUARY = YearMonth.of(2026, 1);
    private static final YearMonth FEBRUARY = YearMonth.of(2026, 2);

    private static final Customer SINGLE = new Customer("40-001", TariffPlan.SINGLE, false, false);
    private static final Customer TWO_ZONE = new Customer("40-002", TariffPlan.TWO_ZONE, false, false);
    private static final Customer THREE_ZONE = new Customer("40-003", TariffPlan.THREE_ZONE, false, false);

    private final ElectricityBillCalculator calculator = new ElectricityBillCalculator();

    /** Reading of the hour starting at the given local time; December is December 2025. */
    private static HourlyReading at(int month, int day, int hour, int kwh) {
        int year = month == 12 ? 2025 : 2026;
        return new HourlyReading(LocalDateTime.of(year, month, day, hour, 0), kwh);
    }

    @Test
    void firstNightHourOfTheMonthIsBilledInThatMonth() {
        var readings = List.of(at(2, 1, 0, 10));

        assertThat(calculator.calculate(TWO_ZONE, readings, FEBRUARY))
                .isEqualTo(new ElectricityBill(10, 0, 0, 2_560, 0, 15_000, 17_560));
        assertThat(calculator.calculate(TWO_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 0, 0, 0, 15_000, 15_000));
    }

    @Test
    void firstNightHourOfTheMonthConsumesTheSocialNormFirst() {
        var readings = List.of(at(2, 1, 8, 100), at(2, 1, 0, 100));

        // 00:00 night: 100 x 2.56; 08:00 peak: 50 x 6.24 + 50 x 7.80
        assertThat(calculator.calculate(THREE_ZONE, readings, FEBRUARY))
                .isEqualTo(new ElectricityBill(100, 100, 0, 95_800, 0, 15_000, 110_800));
    }

    @Test
    void firstHourOfTheNextMonthIsNotChargedAsExcess() {
        var readings = List.of(at(1, 10, 10, 700), at(2, 1, 0, 50));

        // 150 x 5.12 + 450 x 6.40 + 100 x 8.32
        assertThat(calculator.calculate(TWO_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 700, 448_000, 0, 15_000, 463_000));
    }

    @Test
    void newYearNightIsSplitBetweenTheMonths() {
        var readings = List.of(at(12, 31, 23, 10), at(1, 1, 0, 150), at(1, 1, 10, 10));

        // Jan 1 00:00: 150 x 2.56; Jan 1 10:00: 10 x 6.40 (the norm is used up)
        assertThat(calculator.calculate(TWO_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(150, 0, 10, 44_800, 0, 15_000, 59_800));
    }

    @Test
    void lastNightHourOfTheMonthBelongsToThatMonth() {
        var readings = List.of(at(1, 31, 23, 10));

        assertThat(calculator.calculate(THREE_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(10, 0, 0, 2_560, 0, 15_000, 17_560));
        assertThat(calculator.calculate(THREE_ZONE, readings, FEBRUARY))
                .isEqualTo(new ElectricityBill(0, 0, 0, 0, 0, 15_000, 15_000));
    }

    @Test
    void singlePlanAtTheMonthBoundaries() {
        var readings = List.of(at(12, 31, 23, 10), at(1, 1, 0, 10), at(1, 31, 23, 10), at(2, 1, 0, 10));

        assertThat(calculator.calculate(SINGLE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 20, 8_960, 0, 12_000, 20_960));
    }

    @Test
    void wholeMonthOfThreeZoneReadingsWithNeighbouringHours() {
        List<HourlyReading> readings = new ArrayList<>();
        readings.add(at(12, 31, 23, 1));
        for (int day = 1; day <= 31; day++) {
            for (int hour = 0; hour < 24; hour++) {
                readings.add(at(1, day, hour, 1));
            }
        }
        readings.add(at(2, 1, 0, 1));

        assertThat(calculator.calculate(THREE_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(31 * 8, 31 * 7, 31 * 9, 412_312, 0, 15_000, 427_312));
    }

    @Test
    void threeZoneBoundaries() {
        var readings = new ArrayList<HourlyReading>();
        for (int hour : new int[] {6, 7, 9, 10, 16, 17, 20, 21, 22, 23}) {
            readings.add(at(1, 5, hour, 1));
        }

        // night 06, 23; peak 07, 09, 17, 20; half-peak 10, 16, 21, 22 - all within the norm
        assertThat(calculator.calculate(THREE_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(2, 4, 4, 2 * 256 + 4 * 624 + 4 * 448, 0, 15_000,
                        15_000 + 2 * 256 + 4 * 624 + 4 * 448));
    }

    @Test
    void twoZoneBoundaries() {
        var readings = List.of(at(1, 5, 6, 1), at(1, 5, 7, 1), at(1, 5, 22, 1), at(1, 5, 23, 1));

        assertThat(calculator.calculate(TWO_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(2, 0, 2, 2 * 256 + 2 * 512, 0, 15_000, 15_000 + 2 * 256 + 2 * 512));
    }

    @Test
    void electricStoveRaisesTheSocialNorm() {
        var customer = new Customer("40-004", TariffPlan.TWO_ZONE, false, true);

        // 250 x 5.12 + 50 x 6.40
        assertThat(calculator.calculate(customer, List.of(at(1, 5, 10, 300)), JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 300, 160_000, 0, 15_000, 175_000));
    }

    @Test
    void oneHourSplitIntoNormBaseAndExcess() {
        // half-peak: 150 x 4.48 + 450 x 5.60 + 100 x 7.28
        assertThat(calculator.calculate(THREE_ZONE, List.of(at(1, 2, 12, 700)), JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 700, 392_000, 0, 15_000, 407_000));
    }

    @Test
    void coefficientsFollowChronologicalOrderNotInputOrder() {
        var readings = List.of(at(1, 3, 5, 100), at(1, 2, 10, 100));

        // Jan 2 day: 100 x 5.12; Jan 3 night: 50 x 2.56 + 50 x 3.20
        assertThat(calculator.calculate(TWO_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(100, 0, 100, 80_000, 0, 15_000, 95_000));
    }

    @Test
    void ruralDiscountIsRoundedToKopecksHalfUp() {
        var ruralTwoZone = new Customer("40-005", TariffPlan.TWO_ZONE, true, false);
        var ruralSingle = new Customer("40-006", TariffPlan.SINGLE, true, false);

        // 5.12 x 30% = 1.536 -> 1.54; the service fee is not discounted
        assertThat(calculator.calculate(ruralTwoZone, List.of(at(1, 5, 2, 2)), JANUARY))
                .isEqualTo(new ElectricityBill(2, 0, 0, 512, 154, 15_000, 15_358));
        // 4.48 x 30% = 1.344 -> 1.34
        assertThat(calculator.calculate(ruralSingle, List.of(at(1, 5, 2, 1)), JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 1, 448, 134, 12_000, 12_314));
    }

    @Test
    void readingsOfOtherMonthsAreIgnored() {
        var readings = List.of(at(2, 2, 10, 50), at(12, 15, 10, 50));

        assertThat(calculator.calculate(THREE_ZONE, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 0, 0, 0, 15_000, 15_000));
    }
}
