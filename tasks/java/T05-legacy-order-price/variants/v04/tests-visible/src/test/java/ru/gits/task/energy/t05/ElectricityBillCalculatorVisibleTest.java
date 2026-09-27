package ru.gits.task.energy.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class ElectricityBillCalculatorVisibleTest {

    private static final YearMonth JANUARY = YearMonth.of(2026, 1);
    private static final Customer CITY = new Customer("40-001", false);

    private final ElectricityBillCalculator calculator = new ElectricityBillCalculator();

    @Test
    void dayConsumptionWithinTheSocialNorm() {
        var readings = List.of(new HourlyReading(LocalDateTime.of(2026, 1, 10, 10, 0), 5));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(5, 0, 2_560, 0, 12_000, 14_560));
    }

    @Test
    void nightConsumptionIsCheaper() {
        var readings = List.of(new HourlyReading(LocalDateTime.of(2026, 1, 10, 2, 0), 10));

        assertThat(calculator.calculate(CITY, readings, JANUARY))
                .isEqualTo(new ElectricityBill(0, 10, 2_560, 0, 12_000, 14_560));
    }

    @Test
    void consumptionAboveTheSocialNormPaysTheFullPrice() {
        var readings = List.of(
                new HourlyReading(LocalDateTime.of(2026, 1, 5, 10, 0), 100),
                new HourlyReading(LocalDateTime.of(2026, 1, 5, 11, 0), 100));

        // 150 kWh x 5.12 + 50 kWh x 6.40
        assertThat(calculator.calculate(CITY, readings, JANUARY).energyCost()).isEqualTo(108_800);
    }
}
