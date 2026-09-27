package ru.gits.task.shop.t04;

/**
 * Receives price changes from {@link PriceBoard}.
 */
@FunctionalInterface
public interface PriceListener {

    /**
     * @param sku          product whose price changed
     * @param priceKopecks new price
     */
    void priceChanged(String sku, long priceKopecks);
}
