package ru.gits.task.bank.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoanFeeCalculatorHiddenTest {

    private final LoanFeeCalculator calculator = new LoanFeeCalculator();

    /** Characterisation of the regulation where the old code was already right. */
    @ParameterizedTest(name = "{0} kopecks, salary={1}, online={2}, insurance={3}, option={4}")
    @CsvSource({
            // amount, salary, online, insurance, option, base, commission, insurance, option, total
            "300000000, false, false, false, false, 4500000, 3000000, 0,       0,      3000000",
            "200000000, false, false, false, false, 3000000, 3000000, 0,       0,      3000000",
            "300000100, false, false, false, false, 3600001, 3000000, 0,       0,      3000000",
            "50000000,  false, true,  false, false, 750000,  720000,  0,       0,      720000",
            "10000000,  false, true,  false, false, 150000,  150000,  0,       0,      150000",
            "50000000,  true,  false, false, false, 750000,  375000,  0,       0,      375000",
            "10000000,  true,  false, false, false, 150000,  150000,  0,       0,      150000",
            "100000000, true,  true,  false, false, 1500000, 720000,  0,       0,      720000",
            "33333333,  false, false, true,  false, 500000,  500000,  166667,  0,      666667"
    })
    void rulesThatWereAlreadyCorrect(long amount, boolean salary, boolean online, boolean insurance, boolean option,
                                     long base, long commission, long insuranceFee, long optionFee, long total) {
        assertThat(calculator.calculate(new LoanApplication(amount, salary, online, insurance, option)))
                .isEqualTo(new LoanFee(base, commission, insuranceFee, optionFee, total));
    }

    @Test
    void salaryOnlineClientNeverPaysLessThanTheMinimum() {
        // 200 000 RUB: 3000 -> 50% = 1500 -> online -300 = 1200 -> minimum 1500
        assertThat(calculator.calculate(new LoanApplication(20_000_000, true, true, false, false)))
                .isEqualTo(new LoanFee(300_000, 150_000, 0, 0, 150_000));
    }

    @Test
    void salaryOnlineClientWithALargeLoanPaysTheMaximum() {
        // 6 000 000 RUB: 1.2% = 72000 -> 50% = 36000 -> online -300 = 35700 -> maximum 30000
        assertThat(calculator.calculate(new LoanApplication(600_000_000, true, true, false, false)))
                .isEqualTo(new LoanFee(7_200_000, 3_000_000, 0, 0, 3_000_000));
    }

    @Test
    void extrasDoNotHideTheMinimumCommission() {
        assertThat(calculator.calculate(new LoanApplication(20_000_000, true, true, true, true)))
                .isEqualTo(new LoanFee(300_000, 150_000, 100_000, 100_000, 350_000));
    }

    @Test
    void commissionIsAlwaysWithinTheLimits() {
        for (long rubles = 10_000; rubles <= 10_000_000; rubles += 97_000) {
            for (int flags = 0; flags < 16; flags++) {
                LoanFee fee = calculator.calculate(new LoanApplication(rubles * 100,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0));
                assertThat(fee.commission()).as("%d RUB, flags %d", rubles, flags).isBetween(150_000L, 3_000_000L);
                assertThat(fee.total()).isEqualTo(fee.commission() + fee.insurance() + fee.option());
            }
        }
    }
}
