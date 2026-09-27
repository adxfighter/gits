package ru.gits.task.telecom.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyBillCalculatorVisibleTest {

    private final MonthlyBillCalculator calculator = new MonthlyBillCalculator();

    @Test
    void idleMonthCostsTheMonthlyFee() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(List.of(), 0, false));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 0, 0, 0, 0, 0, 35_000));
    }

    @Test
    void longDistanceCallsAreBilledByStartedMinute() {
        var calls = List.of(
                new MonthUsage.Call(MonthUsage.CallType.LONG_DISTANCE, 61),
                new MonthUsage.Call(MonthUsage.CallType.LONG_DISTANCE, 60));

        MonthlyBill bill = calculator.calculate(new MonthUsage(calls, 0, false));

        assertThat(bill.longDistanceCost()).isEqualTo(3 * 400);
        assertThat(bill.total()).isEqualTo(35_000 + 1_200);
    }

    @Test
    void smsBeyondTheFreeOnesArePaid() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(List.of(), 60, false));

        assertThat(bill.smsCost()).isEqualTo(2_000);
    }
}
