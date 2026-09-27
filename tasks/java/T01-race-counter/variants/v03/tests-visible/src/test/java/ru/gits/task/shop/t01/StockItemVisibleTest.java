package ru.gits.task.shop.t01;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockItemVisibleTest {

    @Test
    void reservesWhileGoodsAreAvailable() {
        var item = new StockItem("SKU-42", 3);

        assertThat(item.reserve("o-1", 2)).isPresent();
        assertThat(item.reserve("o-2", 2)).isEmpty();
        assertThat(item.available()).isEqualTo(1);
    }

    @Test
    void releaseReturnsGoodsToStock() {
        var item = new StockItem("SKU-42", 1);
        var reservation = item.reserve("o-1", 1).orElseThrow();

        item.release(reservation);

        assertThat(item.available()).isEqualTo(1);
    }
}
