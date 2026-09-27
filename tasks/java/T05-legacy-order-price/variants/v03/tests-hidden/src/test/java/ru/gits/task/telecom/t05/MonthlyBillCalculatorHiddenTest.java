package ru.gits.task.telecom.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonthlyBillCalculatorHiddenTest {

    private final MonthlyBillCalculator calculator = new MonthlyBillCalculator();

    private static List<MonthUsage.Call> calls(int count, MonthUsage.CallType type, int seconds) {
        return Collections.nCopies(count, new MonthUsage.Call(type, seconds));
    }

    /** Scenarios where every call lasts whole minutes: seconds and minutes give the same result. */
    @ParameterizedTest(name = "{0} local x {1}s, {2} long x {3}s, sms={4}, pensioner={5}")
    @CsvSource({
            // local, localSec, long, longSec, sms, pensioner, paidMin, localCost, benefit, longCost, smsCost, total
            "310, 60,  0, 0,   0,  false, 10, 1500, 0,    0,    0,    36500",
            "320, 60,  0, 0,   0,  true,  20, 3000, 1500, 0,    0,    36500",
            "100, 120, 0, 0,   0,  false, 0,  0,    0,    0,    0,    35000",
            "0,   0,   3, 61,  0,  true,  0,  0,    0,    2400, 0,    37400",
            "0,   0,   0, 0,   51, false, 0,  0,    0,    0,    200,  35200",
            "0,   0,   0, 0,   50, false, 0,  0,    0,    0,    0,    35000",
            "303, 60,  2, 180, 70, true,  3,  450,  225,  2400, 4000, 41625"
    })
    void wholeMinuteScenariosAreUnchanged(int local, int localSeconds, int longCalls, int longSeconds, int sms,
                                          boolean pensioner, long paidMinutes, long localCost, long benefit,
                                          long longCost, long smsCost, long total) {
        List<MonthUsage.Call> all = new ArrayList<>(calls(local, MonthUsage.CallType.LOCAL, localSeconds));
        all.addAll(calls(longCalls, MonthUsage.CallType.LONG_DISTANCE, longSeconds));

        assertThat(calculator.calculate(new MonthUsage(all, sms, pensioner)))
                .isEqualTo(new MonthlyBill(35_000, paidMinutes, localCost, benefit, longCost, smsCost, total));
    }

    @Test
    void everyLocalCallIsRoundedBeforeThePackageIsApplied() {
        // 301 calls of 61 s = 301 x 2 minutes = 602 minutes; 302 are beyond the package
        MonthlyBill bill = calculator.calculate(new MonthUsage(calls(301, MonthUsage.CallType.LOCAL, 61), 0, false));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 302, 45_300, 0, 0, 0, 80_300));
    }

    @Test
    void shortCallsEachCountAsAMinute() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(150, MonthUsage.CallType.LOCAL, 1));
        all.addAll(calls(200, MonthUsage.CallType.LOCAL, 59));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0, false));

        assertThat(bill.paidLocalMinutes()).isEqualTo(50);
        assertThat(bill.localCost()).isEqualTo(7_500);
    }

    @Test
    void pensionerBenefitAppliesToCorrectlyCountedMinutes() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(calls(400, MonthUsage.CallType.LOCAL, 30), 0, true));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 100, 15_000, 7_500, 0, 0, 42_500));
    }

    @Test
    void unansweredCallsAreFree() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(calls(1_000, MonthUsage.CallType.LOCAL, 0), 0, false));

        assertThat(bill.total()).isEqualTo(35_000);
    }

    @Test
    void longDistanceMinutesNeverUseTheLocalPackage() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(10, MonthUsage.CallType.LONG_DISTANCE, 90));
        all.addAll(calls(299, MonthUsage.CallType.LOCAL, 45));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0, true));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 0, 0, 0, 8_000, 0, 43_000));
    }
}
