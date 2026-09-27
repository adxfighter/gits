package ru.gits.task.telecom.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonthlyBillCalculatorHiddenTest {

    private static final MonthUsage.CallType LOCAL = MonthUsage.CallType.LOCAL;
    private static final MonthUsage.CallType LONG = MonthUsage.CallType.LONG_DISTANCE;
    private static final MonthUsage.CallType ROAMING = MonthUsage.CallType.ROAMING;

    private final MonthlyBillCalculator calculator = new MonthlyBillCalculator();

    private static List<MonthUsage.Call> calls(int count, MonthUsage.CallType type, int seconds, boolean weekend) {
        return Collections.nCopies(count, new MonthUsage.Call(type, seconds, weekend));
    }

    @Test
    void shortWeekendLongDistanceCallsTakeWholeMinutesFromThePackage() {
        MonthlyBill bill = calculator.calculate(new MonthUsage(calls(10, LONG, 30, true), 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 10, 0, 0, 0, 0, 0, 0, 35_000));
    }

    @Test
    void packageRunsOutEarlierAfterWeekendLongDistanceCalls() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(100, LONG, 90, true));
        all.addAll(calls(110, LOCAL, 60, false));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 10, 1_500, 0, 0, 0, 36_500));
    }

    @Test
    void mixedMonthWithShortCallsOfAllKinds() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(3, LONG, 45, true));
        all.add(new MonthUsage.Call(LONG, 125, true));
        all.addAll(calls(292, LOCAL, 60, false));
        all.addAll(calls(2, LOCAL, 10, true));
        all.add(new MonthUsage.Call(LONG, 200, false));
        all.add(new MonthUsage.Call(ROAMING, 61, false));
        all.add(new MonthUsage.Call(ROAMING, 30, true));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 55));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 2, 5, 0, 800, 3_400, 1_000, 40_200));
    }

    @Test
    void weekendLongDistanceCallCrossingTheEndOfThePackageIsPaidPartially() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(299, LOCAL, 60, false));
        all.add(new MonthUsage.Call(LONG, 150, true));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 2, 0, 600, 0, 0, 35_600));
    }

    @Test
    void weekendLongDistanceCallThatExactlyExhaustsThePackage() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(290, LOCAL, 60, false));
        all.add(new MonthUsage.Call(LONG, 600, true));
        all.add(new MonthUsage.Call(LONG, 30, true));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 1, 0, 300, 0, 0, 35_300));
    }

    @Test
    void weekdayLongDistanceCallCrossingTheEndOfThePackageIsPaidPartially() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(298, LOCAL, 60, false));
        all.add(new MonthUsage.Call(LONG, 181, false));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 2, 0, 800, 0, 0, 35_800));
    }

    @Test
    void packageIsSpentInTheOrderCallsWereMade() {
        List<MonthUsage.Call> longFirst = new ArrayList<>(calls(100, LONG, 60, false));
        longFirst.addAll(calls(250, LOCAL, 60, false));
        List<MonthUsage.Call> localFirst = new ArrayList<>(calls(250, LOCAL, 60, false));
        localFirst.addAll(calls(100, LONG, 60, false));

        assertThat(calculator.calculate(new MonthUsage(longFirst, 0)))
                .isEqualTo(new MonthlyBill(35_000, 300, 0, 50, 7_500, 0, 0, 0, 42_500));
        assertThat(calculator.calculate(new MonthUsage(localFirst, 0)))
                .isEqualTo(new MonthlyBill(35_000, 300, 0, 50, 0, 20_000, 0, 0, 55_000));
    }

    @Test
    void weekendLocalCallsAreFreeAndDoNotTouchThePackage() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(5, LOCAL, 61, true));
        all.addAll(calls(300, LOCAL, 60, false));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 10, 0, 0, 0, 0, 0, 35_000));
    }

    @Test
    void roamingDoesNotUseThePackageAndIsCappedAcrossWeekdaysAndWeekends() {
        List<MonthUsage.Call> all = new ArrayList<>(calls(100, ROAMING, 60, false));
        all.addAll(calls(40, ROAMING, 60, true));
        all.addAll(calls(300, LOCAL, 60, false));

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 300, 0, 140, 0, 0, 150_000, 0, 185_000));
    }

    @Test
    void unansweredCallsCostNothing() {
        List<MonthUsage.Call> all = new ArrayList<>();
        for (MonthUsage.CallType type : MonthUsage.CallType.values()) {
            all.addAll(calls(5, type, 0, false));
            all.addAll(calls(5, type, 0, true));
        }

        MonthlyBill bill = calculator.calculate(new MonthUsage(all, 0));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, 0, 0, 0, 0, 0, 0, 0, 35_000));
    }

    @ParameterizedTest(name = "{1} x {0} {2}s weekend={3}, sms={4}")
    @CsvSource({
            // type, count, seconds, weekend, sms, package, freeWeekend, paid, local, long, roaming, smsCost, total
            "LOCAL,         310, 60,  false, 0,  300, 0,  10,  1500, 0,    0,      0,   36500",
            "LOCAL,         2,   120, true,  0,  0,   4,  0,   0,    0,    0,      0,   35000",
            "LONG_DISTANCE, 1,   301, false, 0,  6,   0,  0,   0,    0,    0,      0,   35000",
            "LONG_DISTANCE, 305, 60,  true,  0,  300, 0,  5,   0,    1500, 0,      0,   36500",
            "ROAMING,       125, 60,  false, 0,  0,   0,  125, 0,    0,    150000, 0,   185000",
            "ROAMING,       3,   61,  true,  0,  0,   0,  6,   0,    0,    6000,   0,   41000",
            "LOCAL,         0,   0,   false, 50, 0,   0,  0,   0,    0,    0,      0,   35000",
            "LOCAL,         0,   0,   false, 51, 0,   0,  0,   0,    0,    0,      200, 35200"
    })
    void singleRuleScenarios(MonthUsage.CallType type, int count, int seconds, boolean weekend, int sms,
                             long packageUsed, long freeWeekend, long paid, long localCost, long longCost,
                             long roamingCost, long smsCost, long total) {
        MonthlyBill bill = calculator.calculate(new MonthUsage(calls(count, type, seconds, weekend), sms));

        assertThat(bill).isEqualTo(new MonthlyBill(35_000, packageUsed, freeWeekend, paid, localCost, longCost,
                roamingCost, smsCost, total));
    }
}
