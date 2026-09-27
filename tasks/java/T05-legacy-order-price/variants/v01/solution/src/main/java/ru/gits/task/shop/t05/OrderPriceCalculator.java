package ru.gits.task.shop.t05;

/**
 * Calculates the price of an order following the commercial rules:
 * items total, then the volume discount, then the promo code, then delivery.
 */
public class OrderPriceCalculator {

    private static final int VOLUME_DISCOUNT_UNITS = 10;
    private static final int VOLUME_DISCOUNT_PERCENT = 5;
    private static final long FREE_DELIVERY_FROM = 300_000;
    private static final long COURIER_FEE = 30_000;
    private static final long MINUS500_THRESHOLD = 300_000;
    private static final long MINUS500_AMOUNT = 50_000;

    public PriceBreakdown calculate(Order order) {
        long itemsTotal = 0;
        int units = 0;
        for (Order.Line line : order.lines()) {
            itemsTotal += line.priceKopecks() * line.quantity();
            units += line.quantity();
        }

        long volumeDiscount = units >= VOLUME_DISCOUNT_UNITS ? percent(itemsTotal, VOLUME_DISCOUNT_PERCENT) : 0;
        long afterVolume = itemsTotal - volumeDiscount;

        String promo = order.promoCode() == null ? "" : order.promoCode();
        long promoDiscount = promoDiscount(promo, afterVolume);
        long afterDiscounts = afterVolume - promoDiscount;

        long delivery = promo.equals("FREESHIP") ? 0 : deliveryFee(order.delivery(), afterDiscounts);
        return new PriceBreakdown(itemsTotal, volumeDiscount, promoDiscount, delivery, afterDiscounts + delivery);
    }

    private static long promoDiscount(String promo, long amount) {
        return switch (promo) {
            case "SALE10" -> percent(amount, 10);
            case "MINUS500" -> amount >= MINUS500_THRESHOLD ? MINUS500_AMOUNT : 0;
            default -> 0;  // FREESHIP affects delivery only; unknown codes are ignored
        };
    }

    private static long deliveryFee(Order.Delivery delivery, long amount) {
        if (delivery == Order.Delivery.PICKUP) {
            return 0;
        }
        return amount < FREE_DELIVERY_FROM ? COURIER_FEE : 0;
    }

    /** Percentage rounded half up to whole kopecks. */
    private static long percent(long amount, int percent) {
        return (amount * percent + 50) / 100;
    }
}
