package ru.gits.task.shop.t06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SkuHiddenTest {

    private static String fromJson(String json) {
        int start = json.indexOf("\"sku\":\"") + 7;
        return json.substring(start, json.indexOf('"', start));
    }

    private static String fromScanner(char... chars) {
        return new StringBuilder().append(chars).toString();
    }

    @Test
    void skuParsedFromRequestMatchesTheCartLine() {
        var cart = new Cart();
        cart.add(new Sku(fromJson("{\"sku\":\"AB-100\",\"qty\":1}")), 1);
        cart.add(new Sku(fromJson("{\"qty\":2,\"sku\":\"AB-100\"}")), 2);

        assertThat(cart.lineCount()).isEqualTo(1);
        assertThat(cart.quantityOf(new Sku("AB-100"))).isEqualTo(3);
    }

    @Test
    void scannedSkuRemovesTheLine() {
        var cart = new Cart();
        cart.add(new Sku("AB-100"), 1);

        assertThat(cart.remove(new Sku(fromScanner('A', 'B', '-', '1', '0', '0')))).isTrue();
        assertThat(cart.lineCount()).isZero();
    }

    @Test
    void normalizedValuesAreEqual() {
        var typed = new Sku(" ab-" + (100 + Integer.parseInt("0")) + " ");
        var constant = new Sku("AB-100");

        assertThat(typed).isEqualTo(constant);
        assertThat(constant).isEqualTo(typed);
        assertThat(typed.hashCode()).isEqualTo(constant.hashCode());
    }

    @Test
    void manyRuntimeSkusDoNotDuplicate() {
        Set<Sku> catalog = new HashSet<>();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 300; i++) {
                catalog.add(new Sku("sku-" + i));
            }
        }
        assertThat(catalog).hasSize(300);
    }

    @Test
    void equalityMeaningIsKept() {
        assertThat(new Sku("AB-100")).isNotEqualTo(new Sku("AB-101")).isNotEqualTo(null).isNotEqualTo("AB-100");
        var sku = new Sku("x-1");
        assertThat(sku).isEqualTo(sku);
        assertThat(sku.code()).isEqualTo("X-1");
    }
}
