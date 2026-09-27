package ru.gits.task.telecom.t05;

/**
 * Builds the monthly bill of a subscriber. Legacy code: the pensioner branch was added as a copy.
 */
public class MonthlyBillCalculator {

    public MonthlyBill calculate(MonthUsage usage) {
        long fee = 35000;
        long localSeconds = 0;
        long longDistanceMinutes = 0;

        for (MonthUsage.Call call : usage.calls()) {
            if (call.type() == MonthUsage.CallType.LOCAL) {
                localSeconds = localSeconds + call.seconds();
            } else {
                long minutes = call.seconds() / 60;
                if (call.seconds() % 60 != 0) {
                    minutes = minutes + 1;
                }
                longDistanceMinutes = longDistanceMinutes + minutes;
            }
        }

        long paidLocalMinutes;
        long localCost;
        long benefit;
        long longDistanceCost;
        long smsCost;
        long total;

        if (usage.pensioner()) {
            // pensioner tariff
            long paidSeconds = localSeconds - 300 * 60;
            if (paidSeconds < 0) {
                paidSeconds = 0;
            }
            paidLocalMinutes = paidSeconds / 60;
            if (paidSeconds % 60 != 0) {
                paidLocalMinutes = paidLocalMinutes + 1;
            }
            localCost = paidLocalMinutes * 150;
            benefit = (localCost * 50 + 50) / 100;
            longDistanceCost = longDistanceMinutes * 400;
            if (usage.smsCount() > 50) {
                smsCost = (usage.smsCount() - 50) * 200L;
            } else {
                smsCost = 0;
            }
            total = fee + localCost - benefit + longDistanceCost + smsCost;
        } else {
            // regular tariff
            long paidSeconds = localSeconds - 300 * 60;
            if (paidSeconds < 0) {
                paidSeconds = 0;
            }
            paidLocalMinutes = paidSeconds / 60;
            if (paidSeconds % 60 != 0) {
                paidLocalMinutes = paidLocalMinutes + 1;
            }
            localCost = paidLocalMinutes * 150;
            benefit = 0;
            longDistanceCost = longDistanceMinutes * 400;
            if (usage.smsCount() > 50) {
                smsCost = (usage.smsCount() - 50) * 200L;
            } else {
                smsCost = 0;
            }
            total = fee + localCost - benefit + longDistanceCost + smsCost;
        }

        return new MonthlyBill(fee, paidLocalMinutes, localCost, benefit, longDistanceCost, smsCost, total);
    }
}
