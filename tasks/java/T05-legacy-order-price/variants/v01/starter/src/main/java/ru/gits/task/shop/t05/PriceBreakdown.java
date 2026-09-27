package ru.gits.task.shop.t05;

/**
 * Result of the price calculation, all amounts in kopecks.
 *
 * @param itemsTotal     sum of price x quantity
 * @param volumeDiscount discount for 10+ units
 * @param promoDiscount  discount of the promo code
 * @param delivery       delivery fee
 * @param total          amount to pay
 */
public record PriceBreakdown(long itemsTotal, long volumeDiscount, long promoDiscount, long delivery, long total) {
}
