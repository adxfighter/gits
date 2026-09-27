package ru.gits.task.energy.t05;

/**
 * Monthly electricity bill; money in kopecks.
 *
 * @param nightKwh       consumption in the night zone
 * @param peakKwh        consumption in the peak zone
 * @param dayKwh         consumption in the day zone (half-peak zone for the three-zone plan, all hours for the
 *                       single plan)
 * @param energyCost     cost of energy with the norm/excess coefficients, before the rural discount
 * @param ruralDiscount  rural discount
 * @param serviceFee     monthly service fee
 * @param total          amount to pay
 */
public record ElectricityBill(long nightKwh, long peakKwh, long dayKwh, long energyCost, long ruralDiscount,
                              long serviceFee, long total) {
}
