package ru.gits.task.telecom.t05;

/**
 * Itemised monthly bill, amounts in kopecks.
 *
 * @param monthlyFee         subscription fee
 * @param paidLocalMinutes   local minutes beyond the package
 * @param localCost          cost of the paid local minutes before the benefit
 * @param benefit            pensioner discount
 * @param longDistanceCost   cost of long-distance calls
 * @param smsCost            cost of SMS beyond the free ones
 * @param total              amount to pay
 */
public record MonthlyBill(long monthlyFee, long paidLocalMinutes, long localCost, long benefit,
                          long longDistanceCost, long smsCost, long total) {
}
