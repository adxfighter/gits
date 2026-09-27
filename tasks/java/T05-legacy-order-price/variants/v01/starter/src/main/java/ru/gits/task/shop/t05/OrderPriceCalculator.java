package ru.gits.task.shop.t05;

/**
 * Calculates the price of an order. Legacy code: extended many times since the first release.
 */
public class OrderPriceCalculator {

    public PriceBreakdown calculate(Order order) {
        long itemsTotal = 0;
        int units = 0;
        for (int i = 0; i < order.lines().size(); i++) {
            Order.Line line = order.lines().get(i);
            itemsTotal = itemsTotal + line.priceKopecks() * line.quantity();
            units = units + line.quantity();
        }

        long volumeDiscount = 0;
        long promoDiscount = 0;
        long delivery = 0;
        long total;
        String promo = order.promoCode();

        if (promo != null && !promo.isBlank()) {
            // promo code orders
            if (promo.equals("SALE10")) {
                promoDiscount = (itemsTotal * 10 + 50) / 100;
                total = itemsTotal - promoDiscount;
                if (order.delivery() == Order.Delivery.COURIER) {
                    if (total < 300000) {
                        delivery = 30000;
                    } else {
                        delivery = 0;
                    }
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("MINUS500")) {
                if (itemsTotal >= 300000) {
                    promoDiscount = 50000;
                } else {
                    promoDiscount = 0;
                }
                total = itemsTotal - promoDiscount;
                if (order.delivery() == Order.Delivery.COURIER) {
                    if (total < 300000) {
                        delivery = 30000;
                    } else {
                        delivery = 0;
                    }
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("FREESHIP")) {
                total = itemsTotal;
                delivery = 0;
            } else {
                // unknown promo code: ignore it
                total = itemsTotal;
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                if (order.delivery() == Order.Delivery.COURIER) {
                    if (total < 300000) {
                        delivery = 30000;
                    } else {
                        delivery = 0;
                    }
                } else {
                    delivery = 0;
                }
            }
        } else if (units >= 10) {
            // volume discount orders
            volumeDiscount = (itemsTotal * 5 + 50) / 100;
            total = itemsTotal - volumeDiscount;
            if (order.delivery() == Order.Delivery.COURIER) {
                if (total < 300000) {
                    delivery = 30000;
                } else {
                    delivery = 0;
                }
            } else {
                delivery = 0;
            }
        } else {
            // regular orders
            total = itemsTotal;
            if (order.delivery() == Order.Delivery.COURIER) {
                if (total < 300000) {
                    delivery = 30000;
                } else {
                    delivery = 0;
                }
            } else {
                delivery = 0;
            }
        }

        total = total + delivery;
        return new PriceBreakdown(itemsTotal, volumeDiscount, promoDiscount, delivery, total);
    }
}
