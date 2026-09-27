package ru.gits.task.shop.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TopSellersVisibleTest {

    @Test
    void unitsAreSummedAcrossOrders() {
        List<ProductSales> top = new TopSellers().top(List.of(
                new Sale("o1", "MUG", 2),
                new Sale("o2", "MUG", 3),
                new Sale("o2", "TEA", 1)), 10);

        assertThat(top).containsExactlyInAnyOrder(new ProductSales("MUG", 5), new ProductSales("TEA", 1));
    }

    @Test
    void bestSellerComesFirst() {
        List<ProductSales> top = new TopSellers().top(List.of(
                new Sale("o1", "TEA", 1),
                new Sale("o2", "MUG", 7),
                new Sale("o3", "PEN", 3)), 2);

        assertThat(top).containsExactly(new ProductSales("MUG", 7), new ProductSales("PEN", 3));
    }
}
