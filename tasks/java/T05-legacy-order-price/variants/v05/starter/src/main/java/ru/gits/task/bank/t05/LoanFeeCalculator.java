package ru.gits.task.bank.t05;

/**
 * Calculates the loan issuance fee. Legacy code, extended by several teams.
 */
public class LoanFeeCalculator {

    public LoanFee calculate(LoanApplication application) {
        long amount = application.amountKopecks();
        long baseFee;
        if (amount > 300000000L) {
            baseFee = (amount * 12 + 500) / 1000;
        } else {
            baseFee = (amount * 15 + 500) / 1000;
        }

        long commission = baseFee;
        long insurance = 0;
        long option = 0;

        if (application.salaryClient()) {
            // salary clients
            commission = (commission * 50 + 50) / 100;
            if (commission < 150000) {
                commission = 150000;
            }
            if (commission > 3000000) {
                commission = 3000000;
            }
            if (application.online()) {
                commission = commission - 30000;
            }
            if (application.insurance()) {
                insurance = (amount * 5 + 500) / 1000;
            }
            if (application.earlyRepaymentOption()) {
                option = 100000;
            }
        } else {
            // other clients
            if (application.online()) {
                commission = commission - 30000;
            }
            if (commission < 150000) {
                commission = 150000;
            }
            if (commission > 3000000) {
                commission = 3000000;
            }
            if (application.insurance()) {
                insurance = (amount * 5 + 500) / 1000;
            }
            if (application.earlyRepaymentOption()) {
                option = 100000;
            }
        }

        long total = commission + insurance + option;
        return new LoanFee(baseFee, commission, insurance, option, total);
    }
}
