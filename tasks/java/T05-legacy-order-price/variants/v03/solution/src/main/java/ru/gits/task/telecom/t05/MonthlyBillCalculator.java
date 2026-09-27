package ru.gits.task.telecom.t05;

/**
 * Builds the monthly bill of a subscriber.
 */
public class MonthlyBillCalculator {

    public MonthlyBill calculate(MonthUsage usage) {
        // amounts in kopecks
        long fee = 35000;
        // package, minutes
        long packageLeft = 300;
        long packageUsed = 0;
        long freeWeekend = 0;
        long paidMinutes = 0;
        long localCost = 0;
        long longDistanceCost = 0;
        long roamingCost = 0;
        long smsCost = 0;
        long total;

        for (MonthUsage.Call call : usage.calls()) {
            MonthUsage.CallType type = call.type();
            int seconds = call.seconds();

            if (type == MonthUsage.CallType.LOCAL && !call.weekend()) {
                // local call, working day
                long minutes = seconds / 60;
                if (seconds % 60 > 0) {
                    minutes++;
                }
                if (minutes == 0) {
                    continue;
                }
                long paid;
                if (packageLeft >= minutes) {
                    packageLeft = packageLeft - minutes;
                    packageUsed = packageUsed + minutes;
                    paid = 0;
                } else if (packageLeft > 0) {
                    paid = minutes - packageLeft;
                    packageUsed = packageUsed + packageLeft;
                    packageLeft = 0;
                } else {
                    paid = minutes;
                }
                paidMinutes = paidMinutes + paid;
                // 1.50 per minute
                localCost = localCost + paid * 150;

            } else if (type == MonthUsage.CallType.LOCAL) {
                // local call, weekend: free, package is not touched
                long minutes = seconds / 60;
                if (seconds % 60 != 0) {
                    minutes = minutes + 1;
                }
                freeWeekend = freeWeekend + minutes;

            } else if (type == MonthUsage.CallType.LONG_DISTANCE && !call.weekend()) {
                // long distance, working day
                long minutes = (seconds + 59) / 60;
                long paid = 0;
                if (minutes > 0) {
                    if (packageLeft == 0) {
                        paid = minutes;
                    } else if (minutes <= packageLeft) {
                        packageLeft -= minutes;
                        packageUsed += minutes;
                    } else {
                        paid = minutes - packageLeft;
                        packageUsed += packageLeft;
                        packageLeft = 0;
                    }
                }
                paidMinutes += paid;
                // 4.00 per minute
                longDistanceCost = longDistanceCost + paid * 400;

            } else if (type == MonthUsage.CallType.LONG_DISTANCE) {
                // long distance, weekend
                long paid = 0;
                if (seconds > 0) {
                    if (packageLeft > 0) {
                        long secondsLeft = packageLeft * 60;
                        if (seconds <= secondsLeft) {
                            secondsLeft = secondsLeft - seconds;
                            long left = secondsLeft / 60;
                            packageUsed = packageUsed + (packageLeft - left);
                            packageLeft = left;
                        } else {
                            long over = seconds - secondsLeft;
                            paid = over / 60;
                            if (over % 60 != 0) {
                                paid = paid + 1;
                            }
                            packageUsed = packageUsed + packageLeft;
                            packageLeft = 0;
                        }
                    } else {
                        paid = seconds / 60;
                        if (seconds % 60 != 0) {
                            paid = paid + 1;
                        }
                    }
                }
                paidMinutes = paidMinutes + paid;
                // 3.00 per minute
                longDistanceCost = longDistanceCost + paid * 300;

            } else if (type == MonthUsage.CallType.ROAMING && !call.weekend()) {
                // roaming, working day: no package
                long minutes = seconds / 60;
                if (seconds % 60 != 0) {
                    minutes = minutes + 1;
                }
                paidMinutes = paidMinutes + minutes;
                roamingCost = roamingCost + minutes * 1200;
                if (roamingCost > 150000) {
                    roamingCost = 150000;
                }

            } else if (type == MonthUsage.CallType.ROAMING) {
                // roaming, weekend: no package
                long minutes = (seconds + 59) / 60;
                paidMinutes += minutes;
                long cost = minutes * 1000;
                if (roamingCost + cost > 150000) {
                    roamingCost = 150000;
                } else {
                    roamingCost += cost;
                }

            } else {
                throw new IllegalStateException("Unknown call type: " + type);
            }
        }

        // sms: 50 included, 2.00 each after that
        int sms = usage.smsCount();
        int freeSms = 50;
        if (sms > freeSms) {
            long extraSms = sms - freeSms;
            smsCost = extraSms * 200;
        } else {
            smsCost = 0;
        }

        // total
        total = fee;
        total = total + localCost;
        total = total + longDistanceCost;
        total = total + smsCost;
        if (roamingCost > 150000) {
            total = total + 150000;
        } else {
            total = total + roamingCost;
        }

        return new MonthlyBill(fee, packageUsed, freeWeekend, paidMinutes, localCost, longDistanceCost,
                roamingCost, smsCost, total);
    }
}
