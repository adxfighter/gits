package ru.gits.task.telecom.t05;

/**
 * Itemised monthly bill, amounts in kopecks.
 *
 * @param monthlyFee          subscription fee
 * @param packageMinutesUsed  minutes taken from the monthly package
 * @param freeWeekendMinutes  minutes of free local weekend calls
 * @param paidMinutes         minutes charged beyond the package, all call types
 * @param localCost           cost of local calls
 * @param longDistanceCost    cost of long-distance calls
 * @param roamingCost         cost of roaming calls
 * @param smsCost             cost of SMS beyond the free ones
 * @param total               amount to pay
 */
public record MonthlyBill(long monthlyFee, long packageMinutesUsed, long freeWeekendMinutes, long paidMinutes,
                          long localCost, long longDistanceCost, long roamingCost, long smsCost, long total) {
}
