package ru.gits.task.bank.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoanFeeCalculatorVisibleTest {

    private final LoanFeeCalculator calculator = new LoanFeeCalculator();

    @Test
    void regularLoanPaysTheRate() {
        var application = new LoanApplication(50_000_000, false, false, false, false);  // 500 000 RUB

        assertThat(calculator.calculate(application)).isEqualTo(new LoanFee(750_000, 750_000, 0, 0, 750_000));
    }

    @Test
    void smallLoanPaysTheMinimum() {
        var application = new LoanApplication(5_000_000, false, false, false, false);  // 50 000 RUB

        assertThat(calculator.calculate(application).commission()).isEqualTo(150_000);
    }

    @Test
    void insuranceAndOptionAreAddedOnTop() {
        var application = new LoanApplication(100_000_000, false, false, true, true);  // 1 000 000 RUB

        assertThat(calculator.calculate(application))
                .isEqualTo(new LoanFee(1_500_000, 1_500_000, 500_000, 100_000, 2_100_000));
    }
}
