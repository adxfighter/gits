package ru.gits.task.bank.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoanFeeCalculatorVisibleTest {

    private final LoanFeeCalculator calculator = new LoanFeeCalculator();

    @Test
    void regularLoanInTheOfficePaysTheRate() {
        var application = new LoanApplication(50_000_000, false, false, false, false);  // 500 000 RUB

        assertThat(calculator.calculate(application)).isEqualTo(new LoanFee(750_000, 750_000, 0, 0, 750_000));
    }

    @Test
    void smallLoanPaysTheMinimum() {
        var application = new LoanApplication(5_000_000, false, false, false, false);  // 50 000 RUB

        assertThat(calculator.calculate(application)).isEqualTo(new LoanFee(75_000, 150_000, 0, 0, 150_000));
    }

    @Test
    void onlineApplicationGetsTheDiscountAndInsuranceIsAdded() {
        var application = new LoanApplication(100_000_000, false, true, true, false);  // 1 000 000 RUB

        assertThat(calculator.calculate(application))
                .isEqualTo(new LoanFee(1_500_000, 1_470_000, 500_000, 0, 1_970_000));
    }

    @Test
    void largeLoanOfSalaryClientWithExtras() {
        var application = new LoanApplication(400_000_000, true, false, true, true);  // 4 000 000 RUB

        assertThat(calculator.calculate(application))
                .isEqualTo(new LoanFee(4_800_000, 2_400_000, 1_600_000, 200_000, 4_200_000));
    }
}
