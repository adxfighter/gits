package ru.gits.task.shop.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TopSellersHiddenTest {

    @Test
    void productsWithEqualSalesAreAllKept() {
        List<ProductSales> top = new TopSellers().top(List.of(
                new Sale("o1", "PEN", 4),
                new Sale("o2", "MUG", 4),
                new Sale("o3", "CUP", 4),
                new Sale("o4", "TEA", 1)), 10);

        assertThat(top).containsExactly(
                new ProductSales("CUP", 4),
                new ProductSales("MUG", 4),
                new ProductSales("PEN", 4),
                new ProductSales("TEA", 1));
    }

    @Test
    void tieAtTheLimitIsResolvedBySku() {
        List<ProductSales> top = new TopSellers().top(List.of(
                new Sale("o1", "ZIP", 9),
                new Sale("o2", "PEN", 5),
                new Sale("o3", "BAG", 5),
                new Sale("o4", "CAP", 5)), 3);

        assertThat(top).containsExactly(
                new ProductSales("ZIP", 9),
                new ProductSales("BAG", 5),
                new ProductSales("CAP", 5));
    }

    @Test
    void descendingOrderOnALargerCatalog() {
        List<Sale> sales = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            for (int order = 0; order < i; order++) {
                sales.add(new Sale("o" + i + "-" + order, String.format("SKU-%02d", i), 1));
            }
        }

        List<ProductSales> top = new TopSellers().top(sales, 5);

        assertThat(top).extracting(ProductSales::sku)
                .containsExactly("SKU-30", "SKU-29", "SKU-28", "SKU-27", "SKU-26");
        assertThat(top).extracting(ProductSales::units).containsExactly(30, 29, 28, 27, 26);
    }

    @Test
    void catalogSmallerThanNReturnsAllProducts() {
        List<ProductSales> top = new TopSellers().top(List.of(
                new Sale("o1", "TEA", 2),
                new Sale("o2", "MUG", 2),
                new Sale("o3", "TEA", 1)), 5);

        assertThat(top).containsExactly(new ProductSales("TEA", 3), new ProductSales("MUG", 2));
    }

    @Test
    void noSalesGiveAnEmptyBlock() {
        assertThat(new TopSellers().top(List.of(), 3)).isEmpty();
    }

    @Test
    void singleProduct() {
        assertThat(new TopSellers().top(List.of(new Sale("o1", "MUG", 1)), 1))
                .containsExactly(new ProductSales("MUG", 1));
    }
}
