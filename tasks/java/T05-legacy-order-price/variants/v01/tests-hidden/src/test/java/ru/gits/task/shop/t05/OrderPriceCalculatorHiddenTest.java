package ru.gits.task.shop.t05;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.gits.task.shop.t05.Order.Delivery.COURIER;
import static ru.gits.task.shop.t05.Order.Delivery.PICKUP;
import static ru.gits.task.shop.t05.Order.Delivery.POST;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderPriceCalculatorHiddenTest {

    private final OrderPriceCalculator calculator = new OrderPriceCalculator();

    private static Order order(long price, int quantity, String promo, Order.Delivery delivery) {
        return new Order(List.of(new Order.Line("SKU-1", price, quantity)), promo, delivery);
    }

    static Stream<Arguments> rulesOutsideTheDefect() {
        // price, qty, promo, delivery, items, volume, promo, delivery, total
        return Stream.of(
                row(100_000, 1, null, COURIER, 100_000, 0, 0, 30_000, 130_000),
                row(100_000, 3, null, COURIER, 300_000, 0, 0, 0, 300_000),
                row(299_999, 1, null, COURIER, 299_999, 0, 0, 30_000, 329_999),
                row(100_000, 2, null, PICKUP, 200_000, 0, 0, 0, 200_000),
                row(100_000, 3, "MINUS500", COURIER, 300_000, 0, 50_000, 30_000, 280_000),
                row(100_000, 2, "MINUS500", COURIER, 200_000, 0, 0, 30_000, 230_000),
                row(33_333, 3, "SALE10", PICKUP, 99_999, 0, 10_000, 0, 89_999),
                row(100_000, 1, "FREESHIP", COURIER, 100_000, 0, 0, 0, 100_000),
                row(100_000, 1, "FREESHIP", POST, 100_000, 0, 0, 0, 100_000),
                row(100_000, 1, "UNKNOWN", COURIER, 100_000, 0, 0, 30_000, 130_000),
                row(100_000, 5, null, POST, 500_000, 0, 0, 0, 500_000),
                row(499_999, 1, null, POST, 499_999, 0, 0, 30_000, 529_999),
                row(33_350, 1, null, POST, 33_350, 0, 0, 16_001, 49_351),
                row(100_000, 4, "MINUS500", POST, 400_000, 0, 50_000, 25_500, 375_500),
                row(10_000, 12, "UNKNOWN", PICKUP, 120_000, 6_000, 0, 0, 114_000),
                row(10_000, 12, "", POST, 120_000, 6_000, 0, 18_420, 132_420));
    }

    private static Arguments row(long price, int quantity, String promo, Order.Delivery delivery,
                                 long items, long volume, long promoDiscount, long fee, long total) {
        return Arguments.of(order(price, quantity, promo, delivery),
                new PriceBreakdown(items, volume, promoDiscount, fee, total));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rulesOutsideTheDefect")
    void rulesOutsideTheDefectAreUnchanged(Order order, PriceBreakdown expected) {
        assertThat(calculator.calculate(order)).isEqualTo(expected);
    }

    @Test
    void volumeDiscountIsCombinedWithSale10ForCourier() {
        // 12 x 400 = 4800; volume 5% = 240 -> 4560; SALE10 10% = 456 -> 4104 >= 3000: free courier
        assertThat(calculator.calculate(order(40_000, 12, "SALE10", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(480_000, 24_000, 45_600, 0, 410_400));
    }

    @Test
    void minus500ThresholdIsCheckedAfterTheVolumeDiscountForCourier() {
        // 10 x 310 = 3100; volume 155 -> 2945 < 3000: MINUS500 does not apply, courier fee applies
        assertThat(calculator.calculate(order(31_000, 10, "MINUS500", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(310_000, 15_500, 0, 30_000, 324_500));
        // 10 x 400 = 4000; volume 200 -> 3800 >= 3000: MINUS500 applies -> 3300, free courier
        assertThat(calculator.calculate(order(40_000, 10, "MINUS500", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(400_000, 20_000, 50_000, 0, 330_000));
    }

    @Test
    void volumeDiscountIsCombinedWithFreeShipping() {
        assertThat(calculator.calculate(order(10_000, 20, "FREESHIP", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(200_000, 10_000, 0, 0, 190_000));
        assertThat(calculator.calculate(order(10_000, 20, "FREESHIP", Order.Delivery.POST)))
                .isEqualTo(new PriceBreakdown(200_000, 10_000, 0, 0, 190_000));
    }

    @Test
    void unknownPromoCodeKeepsTheVolumeDiscount() {
        assertThat(calculator.calculate(order(10_000, 12, "UNKNOWN", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(120_000, 6_000, 0, 30_000, 144_000));
        assertThat(calculator.calculate(order(10_000, 12, null, Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(120_000, 6_000, 0, 30_000, 144_000));
    }

    @Test
    void discountsCanBringAnOrderBelowTheFreeCourierThreshold() {
        // 10 x 320 = 3200; volume 160 -> 3040; SALE10 304 -> 2736 < 3000: courier fee applies
        assertThat(calculator.calculate(order(32_000, 10, "SALE10", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(320_000, 16_000, 30_400, 30_000, 303_600));
    }

    @Test
    void volumeDiscountIsCombinedWithPromoForPostAndPickup() {
        // 4800 - 240 = 4560; SALE10 456 -> 4104 < 5000: post fee 150 + 123.12
        assertThat(calculator.calculate(order(40_000, 12, "SALE10", Order.Delivery.POST)))
                .isEqualTo(new PriceBreakdown(480_000, 24_000, 45_600, 27_312, 437_712));
        assertThat(calculator.calculate(order(40_000, 10, "MINUS500", Order.Delivery.PICKUP)))
                .isEqualTo(new PriceBreakdown(400_000, 20_000, 50_000, 0, 330_000));
    }

    @Test
    void giftsAreNotCountedAsUnits() {
        var nineAndGifts = new Order(List.of(
                new Order.Line("SKU-1", 10_000, 9),
                new Order.Line("GIFT", 0, 5)), null, Order.Delivery.COURIER);
        assertThat(calculator.calculate(nineAndGifts))
                .isEqualTo(new PriceBreakdown(90_000, 0, 0, 30_000, 120_000));

        var tenAndGifts = new Order(List.of(
                new Order.Line("SKU-1", 10_000, 10),
                new Order.Line("GIFT", 0, 3)), null, Order.Delivery.COURIER);
        assertThat(calculator.calculate(tenAndGifts))
                .isEqualTo(new PriceBreakdown(100_000, 5_000, 0, 30_000, 125_000));
    }

    @Test
    void orderWithoutPaidItemsCostsNothing() {
        assertThat(calculator.calculate(order(0, 2, "SALE10", Order.Delivery.COURIER)))
                .isEqualTo(new PriceBreakdown(0, 0, 0, 0, 0));
        assertThat(calculator.calculate(new Order(List.of(), null, Order.Delivery.POST)))
                .isEqualTo(new PriceBreakdown(0, 0, 0, 0, 0));
    }

    @Test
    void severalLinesAreSummedAndUnitsCounted() {
        var order = new Order(List.of(
                new Order.Line("SKU-1", 10_000, 4),
                new Order.Line("SKU-2", 25_050, 3),
                new Order.Line("SKU-3", 1, 3)), "SALE10", Order.Delivery.PICKUP);

        // items = 40000 + 75150 + 3 = 115153; volume 5% = 5758 (5757.65) -> 109395; SALE10 = 10940 (10939.5)
        assertThat(calculator.calculate(order))
                .isEqualTo(new PriceBreakdown(115_153, 5_758, 10_940, 0, 98_455));
    }
}
