package ru.gits.task.logistics.t05;

/**
 * Itemised delivery cost, all amounts in kopecks.
 *
 * @param base      zone tariff by weight
 * @param fragile   fragile surcharge
 * @param express   express surcharge
 * @param insurance insurance fee
 * @param handling  heavy parcel handling fee
 * @param total     amount to pay
 */
public record DeliveryQuote(long base, long fragile, long express, long insurance, long handling, long total) {
}
