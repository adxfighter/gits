package ru.gits.task.bank.t05;

/**
 * Calculates the loan issuance fee. Legacy code, extended by several teams.
 */
public class LoanFeeCalculator {

    public LoanFee calculate(LoanApplication application) {
        long amount = application.amountKopecks();
        long baseFee = 0;
        long commission = 0;
        long insurance = 0;
        long option = 0;
        boolean web = false;

        String segment;
        if (application.salaryClient()) {
            segment = "SAL";
        } else {
            segment = "STD";
        }
        String channel;
        if (application.online()) {
            channel = "WEB";
        } else {
            channel = "OFC";
        }
        String tariff = segment + "_" + channel;

        switch (tariff) {
            case "STD_OFC":
                // branch office, standard client
                if (amount > 300000000L) {
                    baseFee = amount * 12;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee;
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 5;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 200000;
                    }
                } else {
                    baseFee = amount * 15;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee;
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 5;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 100000;
                    }
                }
                break;

            case "STD_WEB":
                // internet bank, standard client
                if (amount > 300000000L) {
                    baseFee = amount * 12;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee;
                    commission = commission - 30000;
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 5;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 200000;
                    }
                } else {
                    baseFee = amount * 15;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee;
                    commission = commission - 30000;
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 5;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 100000;
                    }
                }
                break;

            case "SAL_WEB":
                web = true;
                // salary clients share the office tariff
            case "SAL_OFC":
                if (amount > 300000000L) {
                    baseFee = amount * 12;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee * 50;
                    commission = (commission + 50) / 100;
                    if (web) {
                        commission = commission - 30000;
                    }
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 4;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 200000;
                    }
                } else {
                    baseFee = amount * 15;
                    baseFee = (baseFee + 500) / 1000;
                    commission = baseFee * 50;
                    commission = (commission + 50) / 100;
                    if (web) {
                        commission = commission - 30000;
                    }
                    if (commission < 150000) {
                        commission = 150000;
                    }
                    if (commission > 3000000) {
                        commission = 3000000;
                    }
                    if (application.insurance()) {
                        long ins = amount * 4;
                        insurance = (ins + 500) / 1000;
                    }
                    if (application.earlyRepaymentOption()) {
                        option = 100000;
                    }
                }
                break;

            default:
                throw new IllegalStateException("Unknown tariff: " + tariff);
        }

        long total = commission + insurance + option;
        return new LoanFee(baseFee, commission, insurance, option, total);
    }
}
