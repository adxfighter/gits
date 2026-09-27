package ru.gits.task.telecom.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyBillCalculatorVisibleTest {

    private final MonthlyBillCalculator calculator = new MonthlyBillCalculator();

    private static List<MonthUsage.Call> calls(int count, MonthUsage.CallType type, int seconds, boolean weekend) {
        return Collections.nCopies(count, new MonthUsage.Call(type, seconds, weekend));
    }

    @Test
    void idleMonthCostsTheMonthlyFee() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(List.of(), 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 0, 0, 0, 0, 0, 0, 0, 35_000));
    }

    @Test
    void localWeekdayMinutesBeyondThePackageArePaid() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(299, MonthUsage.CallType.LOCAL, 60, false));
        all.add(new MonthUsage.Call(MonthUsage.CallType.LOCAL, 61, false));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 1, 150, 0, 0, 0, 35_150));
    }

    @Test
    void roamingCostIsCappedPerMonth() {
        MonthlyBill bill = calculator.calculate(
                new MonthUsage(calls(130, MonthUsage.CallType.ROAMING, 60, false), 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 0, 0, 130, 0, 0, 150_000, 0, 185_000));
    }

    @Test
    void smsBeyondTheFreeOnesArePaid() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(List.of(), 60));

        assertThat(bill.smsCost()).isEqualTo(2_000);
        assertThat(bill.total()).isEqualTo(37_000);
    }
}
