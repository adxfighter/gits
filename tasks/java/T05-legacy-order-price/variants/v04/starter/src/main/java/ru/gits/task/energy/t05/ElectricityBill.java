package ru.gits.task.energy.t05;

/**
 * Monthly electricity bill; money in kopecks.
 *
 * @param dayKwh         consumption in the day zone
 * @param nightKwh       consumption in the night zone
 * @param energyCost     cost of energy with the norm/excess coefficients, before the rural discount
 * @param ruralDiscount  rural discount
 * @param serviceFee     monthly service fee
 * @param total          amount to pay
 */
public record ElectricityBill(long dayKwh, long nightKwh, long energyCost, long ruralDiscount, long serviceFee,
                              long total) {
}
