package ru.gits.task.bank.t05;

/**
 * Calculates the loan issuance fee in the order of the regulation:
 * rate, salary discount, online discount, min/max limits, then insurance and the option on top.
 */
public class LoanFeeCalculator {

    private static final long REDUCED_RATE_FROM = 300_000_000L;
    private static final int RATE_PER_MILLE = 15;
    private static final int REDUCED_RATE_PER_MILLE = 12;
    private static final int SALARY_DISCOUNT_PERCENT = 50;
    private static final long ONLINE_DISCOUNT = 30_000;
    private static final long MIN_COMMISSION = 150_000;
    private static final long MAX_COMMISSION = 3_000_000;
    private static final int INSURANCE_PER_MILLE = 5;
    private static final long EARLY_REPAYMENT_OPTION = 100_000;

    public LoanFee calculate(LoanApplication application) {
        long amount = application.amountKopecks();
        long baseFee = perMille(amount, amount > REDUCED_RATE_FROM ? REDUCED_RATE_PER_MILLE : RATE_PER_MILLE);

        long commission = baseFee;
        if (application.salaryClient()) {
            commission = (commission * SALARY_DISCOUNT_PERCENT + 50) / 100;
        }
        if (application.online()) {
            commission -= ONLINE_DISCOUNT;
        }
        commission = Math.clamp(commission, MIN_COMMISSION, MAX_COMMISSION);  // limits apply after all discounts

        long insurance = application.insurance() ? perMille(amount, INSURANCE_PER_MILLE) : 0;
        long option = application.earlyRepaymentOption() ? EARLY_REPAYMENT_OPTION : 0;
        return new LoanFee(baseFee, commission, insurance, option, commission + insurance + option);
    }

    /** Per-mille share rounded half up to whole kopecks. */
    private static long perMille(long amount, int perMille) {
        return (amount * perMille + 500) / 1000;
    }
}
