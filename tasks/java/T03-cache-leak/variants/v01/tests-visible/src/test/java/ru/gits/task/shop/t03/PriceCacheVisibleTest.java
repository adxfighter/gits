package ru.gits.task.shop.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PriceCacheVisibleTest {

    @Test
    void loadsAPriceOnceWhileItIsCached() {
        List<String> requests = new ArrayList<>();
        var cache = new PriceCache(sku -> {
            requests.add(sku);
            return 99_900;
        }, 10);

        assertThat(cache.price("SKU-1")).isEqualTo(99_900);
        assertThat(cache.price("SKU-1")).isEqualTo(99_900);

        assertThat(requests).containsExactly("SKU-1");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void invalidatedPriceIsLoadedAgain() {
        List<String> requests = new ArrayList<>();
        var cache = new PriceCache(sku -> {
            requests.add(sku);
            return 100;
        }, 10);

        cache.price("SKU-1");
        cache.invalidate("SKU-1");
        cache.price("SKU-1");

        assertThat(requests).containsExactly("SKU-1", "SKU-1");
    }
}
