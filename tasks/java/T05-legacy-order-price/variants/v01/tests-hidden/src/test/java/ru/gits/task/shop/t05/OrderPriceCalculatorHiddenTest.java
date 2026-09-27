package ru.gits.task.shop.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderPriceCalculatorHiddenTest {

    private final OrderPriceCalculator calculator = new OrderPriceCalculator();

    private static Order order(long price, int quantity, String promo, Order.Delivery delivery) {
        return new Order(List.of(new Order.Line("SKU-1", price, quantity)), promo, delivery);
    }

    /** Characterisation of every rule outside the volume-discount + promo combination. */
    @ParameterizedTest(name = "{0} x {1}, promo={2}, {3}")
    @CsvSource(nullValues = "null", value = {
            // price, qty, promo, delivery, items, volume, promo, delivery, total
            "100000, 1, null,     COURIER, 100000, 0,     0,     30000, 130000",
            "100000, 3, null,     COURIER, 300000, 0,     0,     0,     300000",
            "299999, 1, null,     COURIER, 299999, 0,     0,     30000, 329999",
            "100000, 2, null,     PICKUP,  200000, 0,     0,     0,     200000",
            "100000, 3, SALE10,   COURIER, 300000, 0,     30000, 30000, 300000",
            "33333,  3, SALE10,   PICKUP,  99999,  0,     10000, 0,     89999",
            "100000, 3, MINUS500, COURIER, 300000, 0,     50000, 30000, 280000",
            "100000, 2, MINUS500, COURIER, 200000, 0,     0,     30000, 230000",
            "100000, 1, FREESHIP, COURIER, 100000, 0,     0,     0,     100000",
            "100000, 1, UNKNOWN,  COURIER, 100000, 0,     0,     30000, 130000",
            "10000,  12, UNKNOWN, COURIER, 120000, 6000,  0,     30000, 144000",
            "10000,  12, '',      COURIER, 120000, 6000,  0,     30000, 144000"
    })
    void rulesOutsideTheDefectAreUnchanged(long price, int quantity, String promo, Order.Delivery delivery,
                                           long items, long volume, long promoDiscount, long fee, long total) {
        assertThat(calculator.calculate(order(price, quantity, promo, delivery)))
                .isEqualTo(new PriceBreakdown(items, volume, promoDiscount, fee, total));
    }

    @Test
    void volumeDiscountIsCombinedWithSale10() {
        // 10 x 400 = 4000; volume 5% = 200; SALE10 10% of 3800 = 380
        assertThat(calculator.calculate(order(40_000, 10, "SALE10", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(400_000, 20_000, 38_000, 0, 342_000));
    }

    @Test
    void minus500ThresholdIsCheckedAfterTheVolumeDiscount() {
        // 10 x 310 = 3100; volume 5% = 155 -> 2945 < 3000: MINUS500 does not apply; courier fee applies
        assertThat(calculator.calculate(order(31_000, 10, "MINUS500", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(310_000, 15_500, 0, 30_000, 324_500));
        // 10 x 400 = 4000; volume 200 -> 3800 >= 3000: MINUS500 applies -> 3300
        assertThat(calculator.calculate(order(40_000, 10, "MINUS500", Order.Delivery.PICKUP)))
                .isEqualTo(new PriceBreakdown(400_000, 20_000, 50_000, 0, 330_000));
    }

    @Test
    void volumeDiscountIsCombinedWithFreeShipping() {
        assertThat(calculator.calculate(order(10_000, 20, "FREESHIP", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(200_000, 10_000, 0, 0, 190_000));
    }

    @Test
    void discountsCanBringAnOrderBelowTheFreeDeliveryThreshold() {
        // 10 x 320 = 3200; volume 160 -> 3040; SALE10 304 -> 2736 < 3000: courier fee applies
        assertThat(calculator.calculate(order(32_000, 10, "SALE10", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(320_000, 16_000, 30_400, 30_000, 303_600));
    }

    @Test
    void severalLinesAreSummedAndUnitsCounted() {
        var order = new Order(List.of(
                new Order.Line("SKU-1", 10_000, 4),
                new Order.Line("SKU-2", 25_050, 3),
                new Order.Line("SKU-3", 1, 3)), "SALE10", Order.Delivery.PICKUP);

        // items = 40000 + 75150 + 3 = 115153; volume 5% = 5758 (5757.65 rounded) -> 109395; SALE10 = 10940 (10939.5)
        assertThat(calculator.calculate(order))
                .isEqualTo(new PriceBreakdown(115_153, 5_758, 10_940, 0, 98_455));
    }
}
