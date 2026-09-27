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
            long price = line.priceKopecks();
            int qty = line.quantity();
            itemsTotal = itemsTotal + price * qty;
            if (price > 0) {
                units = units + qty;
            }
        }
        if (units == 0) {
            return new PriceBreakdown(itemsTotal, 0, 0, 0, itemsTotal);
        }

        String promo = order.promoCode();
        if (promo == null) {
            promo = "";
        }

        long volumeDiscount = 0;
        long promoDiscount = 0;
        long delivery = 0;
        long total = itemsTotal;

        if (order.delivery() == Order.Delivery.COURIER) {
            if (units >= 10) {
                volumeDiscount = (itemsTotal * 5 + 50) / 100;
                total = itemsTotal - volumeDiscount;
            }
            if (promo.equals("SALE10")) {
                promoDiscount = (total * 10 + 50) / 100;
                total = total - promoDiscount;
                if (total < 300000) {
                    delivery = 30000;
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("MINUS500")) {
                if (total >= 300000) {
                    promoDiscount = 50000;
                } else {
                    promoDiscount = 0;
                }
                total = total - promoDiscount;
                if (total < 300000) {
                    delivery = 30000;
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("FREESHIP")) {
                promoDiscount = 0;
                delivery = 0;
            } else {
                promoDiscount = 0;
                if (total < 300000) {
                    delivery = 30000;
                } else {
                    delivery = 0;
                }
            }
        } else if (order.delivery() == Order.Delivery.POST) {
            if (promo.equals("SALE10")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = (total * 10 + 50) / 100;
                total = total - promoDiscount;
                if (total < 500000) {
                    long postPercent = (total * 3 + 50) / 100;
                    delivery = 15000 + postPercent;
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("MINUS500")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                if (total >= 300000) {
                    promoDiscount = 50000;
                } else {
                    promoDiscount = 0;
                }
                total = total - promoDiscount;
                if (total < 500000) {
                    long postPercent = (total * 3 + 50) / 100;
                    delivery = 15000 + postPercent;
                } else {
                    delivery = 0;
                }
            } else if (promo.equals("FREESHIP")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = 0;
                delivery = 0;
            } else {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = 0;
                if (total < 500000) {
                    long postPercent = (total * 3 + 50) / 100;
                    delivery = 15000 + postPercent;
                } else {
                    delivery = 0;
                }
            }
        } else {
            if (promo.equals("SALE10")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = (total * 10 + 50) / 100;
                total = total - promoDiscount;
                delivery = 0;
            } else if (promo.equals("MINUS500")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                if (total >= 300000) {
                    promoDiscount = 50000;
                } else {
                    promoDiscount = 0;
                }
                total = total - promoDiscount;
                delivery = 0;
            } else if (promo.equals("FREESHIP")) {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = 0;
                delivery = 0;
            } else {
                if (units >= 10) {
                    volumeDiscount = (itemsTotal * 5 + 50) / 100;
                    total = itemsTotal - volumeDiscount;
                }
                promoDiscount = 0;
                delivery = 0;
            }
        }

        total = total + delivery;
        return new PriceBreakdown(itemsTotal, volumeDiscount, promoDiscount, delivery, total);
    }
}
