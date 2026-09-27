package ru.gits.task.energy.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class ElectricityBillCalculatorVisibleTest {

    private static final YearMonth JANUARY = YearMonth.of(2026, 1);

    private final ElectricityBillCalculator calculator = new ElectricityBillCalculator();

    private static HourlyReading reading(int day, int hour, int kwh) {
        return new HourlyReading(LocalDateTime.of(2026, 1, day, hour, 0), kwh);
    }

    @Test
    void singlePlanWithinTheSocialNorm() {
        var customer = new Customer("40-001", TariffPlan.SINGLE, false, false);

        // 5 kWh x 5.60 x 0.8
        assertThat(calculator.calculate(customer, List.of(reading(10, 10, 5)), JANUARY))
                .isEqualTo(new ElectricityBill(0, 0, 5, 2_240, 0, 12_000, 14_240));
    }

    @Test
    void twoZonePlanNightHour() {
        var customer = new Customer("40-002", TariffPlan.TWO_ZONE, false, false);

        // 10 kWh x 3.20 x 0.8
        assertThat(calculator.calculate(customer, List.of(reading(10, 2, 10)), JANUARY))
                .isEqualTo(new ElectricityBill(10, 0, 0, 2_560, 0, 15_000, 17_560));
    }

    @Test
    void threeZonePlanPeakAndHalfPeakAboveTheSocialNorm() {
        var customer = new Customer("40-003", TariffPlan.THREE_ZONE, false, false);
        var readings = List.of(reading(10, 12, 100), reading(10, 8, 100));

        // 08:00 peak: 100 x 7.80 x 0.8; 12:00 half-peak: 50 x 5.60 x 0.8 + 50 x 5.60
        assertThat(calculator.calculate(customer, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 100, 100, 112_800, 0, 15_000, 127_800));
    }
}
