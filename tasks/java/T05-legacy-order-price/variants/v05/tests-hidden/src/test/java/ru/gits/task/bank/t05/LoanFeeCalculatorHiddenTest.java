package ru.gits.task.bank.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoanFeeCalculatorHiddenTest {

    private final LoanFeeCalculator calculator = new LoanFeeCalculator();

    private LoanFee fee(long amount, boolean salary, boolean online, boolean insurance, boolean option) {
        return calculator.calculate(new LoanApplication(amount, salary, online, insurance, option));
    }

    /** Characterisation of the regulation for the cases the old code already handled right. */
    @ParameterizedTest(name = "{0} kopecks, salary={1}, online={2}, insurance={3}, option={4}")
    @CsvSource({
            // amount, salary, online, insurance, option, base, commission, insurance, option, total
            "300000000, false, false, false, false, 4500000, 3000000, 0, 0, 3000000",
            "300000000, false, false, false, true, 4500000, 3000000, 0, 100000, 3100000",
            "300000001, false, false, false, true, 3600000, 3000000, 0, 200000, 3200000",
            "33333333, false, false, true, false, 500000, 500000, 166667, 0, 666667",
            "50000000, false, true, false, false, 750000, 720000, 0, 0, 720000",
            "11000000, false, true, false, false, 165000, 150000, 0, 0, 150000",
            "200000000, false, true, false, false, 3000000, 2970000, 0, 0, 2970000",
            "250000000, false, true, false, false, 3750000, 3000000, 0, 0, 3000000",
            "500000000, false, true, false, true, 6000000, 3000000, 0, 200000, 3200000"
    })
    void standardClientRules(long amount, boolean salary, boolean online, boolean insurance, boolean option,
                             long base, long commission, long insuranceFee, long optionFee, long total) {
        assertThat(fee(amount, salary, online, insurance, option))
                .isEqualTo(new LoanFee(base, commission, insuranceFee, optionFee, total));
    }

    @ParameterizedTest(name = "{0} kopecks, salary={1}, online={2}, insurance={3}, option={4}")
    @CsvSource({
            // amount, salary, online, insurance, option, base, commission, insurance, option, total
            "50000000, true, false, false, false, 750000, 375000, 0, 0, 375000",
            "10000000, true, false, false, false, 150000, 150000, 0, 0, 150000",
            "600000000, true, false, false, false, 7200000, 3000000, 0, 0, 3000000",
            "33333333, true, false, true, false, 500000, 250000, 133333, 0, 383333",
            "100000066, true, false, false, false, 1500001, 750001, 0, 0, 750001",
            "100000000, true, true, true, false, 1500000, 720000, 400000, 0, 1120000"
    })
    void salaryClientRules(long amount, boolean salary, boolean online, boolean insurance, boolean option,
                           long base, long commission, long insuranceFee, long optionFee, long total) {
        assertThat(fee(amount, salary, online, insurance, option))
                .isEqualTo(new LoanFee(base, commission, insuranceFee, optionFee, total));
    }

    @ParameterizedTest(name = "{0} kopecks")
    @CsvSource({
            // amount, base, commission
            "5000000, 75000, 150000",
            "20000000, 300000, 150000",
            "22000000, 330000, 150000",
            "24000000, 360000, 150000",
            "26000000, 390000, 165000"
    })
    void salaryOnlineClientNeverPaysLessThanTheMinimum(long amount, long base, long commission) {
        assertThat(fee(amount, true, true, false, false)).isEqualTo(new LoanFee(base, commission, 0, 0, commission));
    }

    @ParameterizedTest(name = "{0} kopecks")
    @CsvSource({
            // amount, base, commission
            "502000000, 6024000, 2982000",
            "506000000, 6072000, 3000000",
            "600000000, 7200000, 3000000"
    })
    void salaryOnlineClientWithALargeLoanPaysUpToTheMaximum(long amount, long base, long commission) {
        assertThat(fee(amount, true, true, false, false)).isEqualTo(new LoanFee(base, commission, 0, 0, commission));
    }

    @Test
    void extrasDoNotHideTheMinimumCommission() {
        assertThat(fee(20_000_000, true, true, true, true))
                .isEqualTo(new LoanFee(300_000, 150_000, 80_000, 100_000, 330_000));
    }

    @Test
    void extrasAreChargedOnTopOfTheMaximumCommission() {
        assertThat(fee(600_000_000, true, true, true, true))
                .isEqualTo(new LoanFee(7_200_000, 3_000_000, 2_400_000, 200_000, 5_600_000));
    }

    @Test
    void commissionIsAlwaysWithinTheLimits() {
        for (long rubles = 10_000; rubles <= 10_000_000; rubles += 97_000) {
            for (int flags = 0; flags < 16; flags++) {
                LoanFee fee = fee(rubles * 100,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
                assertThat(fee.commission()).as("%d RUB, flags %d", rubles, flags).isBetween(150_000L, 3_000_000L);
                assertThat(fee.total()).isEqualTo(fee.commission() + fee.insurance() + fee.option());
            }
        }
    }
}
