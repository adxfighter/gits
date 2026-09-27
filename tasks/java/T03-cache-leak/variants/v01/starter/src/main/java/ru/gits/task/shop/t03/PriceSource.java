package ru.gits.task.shop.t03;

/**
 * Pricing service of the shop. Each call is a slow remote request.
 */
@FunctionalInterface
public interface PriceSource {

    /**
     * Current price of a product.
     *
     * @param sku stock keeping unit
     * @return price in kopecks
     */
    long priceOf(String sku);
}
