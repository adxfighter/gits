package ru.gits.task.energy.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ElectricityBillCalculatorHiddenTest {

    private static final YearMonth JANUARY = YearMonth.of(2026, 1);
    private static final Customer CITY = new Customer("40-001", false);
    private static final Customer RURAL = new Customer("40-002", true);

    private final ElectricityBillCalculator calculator = new ElectricityBillCalculator();

    private static HourlyReading at(int month, int day, int hour, int kwh) {
        int year = month == 12 ? 2025 : 2026;
        return new HourlyReading(LocalDateTime.of(year, month, day, hour, 0), kwh);
    }

    @Test
    void lastNightHourOfTheMonthBelongsToThatMonth() {
        var readings = List.of(at(1, 31, 23, 10));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 10, 2_560, 0, 12_000, 14_560));
        assertThat(calculator.calculate(CITY, readings, YearMonth.of(2026, 2)).nightKwh()).isZero();
    }

    @Test
    void lastHourOfThePreviousMonthDoesNotUseThisMonthsNorm() {
        var readings = List.of(at(12, 31, 23, 10), at(1, 10, 10, 150));

        // all 150 kWh of January are within the social norm
        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(150, 0, 76_800, 0, 12_000, 88_800));
    }

    @Test
    void firstNightHoursOfTheMonthAreIncluded() {
        var readings = List.of(at(1, 1, 0, 4), at(1, 1, 6, 4), at(1, 1, 7, 4));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(4, 8, 8 * 256 + 4 * 512, 0, 12_000, 12_000 + 8 * 256 + 4 * 512));
    }

    @Test
    void wholeMonthOfNightAndDayHoursIsBilledCompletely() {
        List<HourlyReading> readings = new ArrayList<>();
        for (int day = 1; day <= 31; day++) {
            for (int hour = 0; hour < 24; hour++) {
                readings.add(at(1, day, hour, 1));
            }
        }

        ElectricityBill bill = calculator.calculate(CITY, readings, JANUARY);

        assertThat(bill.nightKwh()).isEqualTo(31 * 8);
        assertThat(bill.dayKwh()).isEqualTo(31 * 16);
        assertThat(bill.nightKwh() + bill.dayKwh()).isEqualTo(744);
    }

    @Test
    void excessAboveSixHundredKwh() {
        // 150 x 5.12 + 450 x 6.40 + 100 x 8.32
        assertThat(calculator.calculate(CITY, List.of(at(1, 2, 10, 700)), JANUARY))
                .isEqualTo(new ElectricityBill(700, 0, 448_000, 0, 12_000, 460_000));
    }

    @Test
    void ruralDiscountAppliesToTheEnergyCost() {
        var readings = List.of(at(1, 5, 10, 100), at(1, 5, 11, 100));

        assertThat(calculator.calculate(RURAL, readings, JANUARY))
                .isEqualTo(new ElectricityBill(200, 0, 108_800, 32_640, 12_000, 88_160));
    }

    @Test
    void coefficientsFollowChronologicalOrderNotInputOrder() {
        var readings = List.of(at(1, 3, 5, 100), at(1, 2, 10, 100));

        // Jan 2 day: 100 x 5.12; Jan 3 night: 50 x 2.56 + 50 x 3.20
        assertThat(calculator.calculate(CITY, readings, JANUARY).energyCost()).isEqualTo(51_200 + 12_800 + 16_000);
    }

    @Test
    void zoneBoundaries() {
        var readings = List.of(at(1, 5, 6, 1), at(1, 5, 7, 1), at(1, 5, 22, 1), at(1, 5, 23, 1));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(2, 2, 2 * 256 + 2 * 512, 0, 12_000, 12_000 + 2 * 256 + 2 * 512));
    }

    @Test
    void readingsOfOtherMonthsAreIgnored() {
        var readings = List.of(at(2, 1, 10, 50), at(12, 15, 10, 50));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 0, 0, 12_000, 12_000));
    }
}
