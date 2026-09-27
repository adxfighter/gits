package ru.gits.task.shop.t05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class OrderPriceCalculatorVisibleTest {

    private final OrderPriceCalculator calculator = new OrderPriceCalculator();

    @Test
    void smallCourierOrderPaysForDelivery() {
        var order = new Order(List.of(new Order.Line("SKU-1", 120_000, 2)), null, Order.Delivery.COURIER);

        assertThat(calculator.calculate(order)).isEqualTo(new PriceBreakdown(240_000, 0, 0, 30_000, 270_000));
    }

    @Test
    void volumeDiscountForTenUnitsWithPickup() {
        var order = new Order(List.of(new Order.Line("SKU-1", 50_000, 10)), null, Order.Delivery.PICKUP);

        assertThat(calculator.calculate(order)).isEqualTo(new PriceBreakdown(500_000, 25_000, 0, 0, 475_000));
    }

    @Test
    void promoCodeSale10WithPostDelivery() {
        // 3000 - 10% = 2700; post fee 150 + 3% of 2700 = 231
        var order = new Order(List.of(new Order.Line("SKU-1", 100_000, 3)), "SALE10", Order.Delivery.POST);

        assertThat(calculator.calculate(order)).isEqualTo(new PriceBreakdown(300_000, 0, 30_000, 23_100, 293_100));
    }

    @Test
    void promoCodeSale10WithCourierBelowFreeDelivery() {
        var order = new Order(List.of(new Order.Line("SKU-1", 100_000, 3)), "SALE10", Order.Delivery.COURIER);

        assertThat(calculator.calculate(order)).isEqualTo(new PriceBreakdown(300_000, 0, 30_000, 30_000, 300_000));
    }
}
