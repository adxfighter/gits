package ru.gits.task.shop.t06;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CartVisibleTest {

    @Test
    void addingTheSameSkuIncreasesQuantity() {
        var cart = new Cart();
        cart.add(new Sku("AB-100"), 1);
        cart.add(new Sku("AB-100"), 2);

        assertThat(cart.lineCount()).isEqualTo(1);
        assertThat(cart.quantityOf(new Sku("AB-100"))).isEqualTo(3);
    }

    @Test
    void differentSkusAreDifferentLines() {
        var cart = new Cart();
        cart.add(new Sku("AB-100"), 1);
        cart.add(new Sku("AB-200"), 1);

        assertThat(cart.lineCount()).isEqualTo(2);
    }
}
