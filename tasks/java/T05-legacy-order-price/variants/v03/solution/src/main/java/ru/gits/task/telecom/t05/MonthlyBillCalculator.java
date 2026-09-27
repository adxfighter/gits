package ru.gits.task.telecom.t05;

/**
 * Builds the monthly bill of a subscriber.
 */
public class MonthlyBillCalculator {

    private static final long MONTHLY_FEE = 35_000;
    private static final long LOCAL_PACKAGE_MINUTES = 300;
    private static final long LOCAL_MINUTE_PRICE = 150;
    private static final long LONG_DISTANCE_MINUTE_PRICE = 400;
    private static final int FREE_SMS = 50;
    private static final long SMS_PRICE = 200;
    private static final int PENSIONER_DISCOUNT_PERCENT = 50;

    public MonthlyBill calculate(MonthUsage usage) {
        long localMinutes = 0;
        long longDistanceMinutes = 0;
        for (MonthUsage.Call call : usage.calls()) {
            long minutes = billedMinutes(call.seconds());
            if (call.type() == MonthUsage.CallType.LOCAL) {
                localMinutes += minutes;
            } else {
                longDistanceMinutes += minutes;
            }
        }

        // the package is counted in minutes, after every call has been rounded
        long paidLocalMinutes = Math.max(0, localMinutes - LOCAL_PACKAGE_MINUTES);
        long localCost = paidLocalMinutes * LOCAL_MINUTE_PRICE;
        long benefit = usage.pensioner() ? percent(localCost, PENSIONER_DISCOUNT_PERCENT) : 0;
        long longDistanceCost = longDistanceMinutes * LONG_DISTANCE_MINUTE_PRICE;
        long smsCost = Math.max(0, usage.smsCount() - FREE_SMS) * SMS_PRICE;

        long total = MONTHLY_FEE + localCost - benefit + longDistanceCost + smsCost;
        return new MonthlyBill(MONTHLY_FEE, paidLocalMinutes, localCost, benefit, longDistanceCost, smsCost, total);
    }

    /** Every started minute is billed. */
    private static long billedMinutes(int seconds) {
        return (seconds + 59L) / 60;
    }

    private static long percent(long amount, int percent) {
        return (amount * percent + 50) / 100;
    }
}
