package ru.gits.task.shop.t03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PriceCacheHiddenTest {

    private final List<String> requests = new ArrayList<>();
    private final PriceSource source = sku -> {
        requests.add(sku);
        return sku.length() * 100L;
    };

    @Test
    void cacheNeverGrowsBeyondItsCapacity() {
        var cache = new PriceCache(source, 1_000);

        for (int i = 0; i < 50_000; i++) {
            cache.price("SKU-" + i);
            assertThat(cache.size()).isLessThanOrEqualTo(1_000);
        }
        assertThat(cache.size()).isEqualTo(1_000);
    }

    @Test
    void leastRecentlyUsedPriceIsEvictedFirst() {
        var cache = new PriceCache(source, 2);

        cache.price("A");
        cache.price("B");
        cache.price("A");  // A is now more recent than B
        cache.price("C");  // evicts B
        requests.clear();

        cache.price("A");
        cache.price("C");
        cache.price("B");

        assertThat(requests).containsExactly("B");
    }

    @Test
    void popularProductsStayCachedWhileTheCatalogueIsBrowsed() {
        var cache = new PriceCache(source, 100);
        cache.price("HIT-1");
        cache.price("HIT-2");

        for (int i = 0; i < 10_000; i++) {
            cache.price("SKU-" + i);
            cache.price("HIT-1");
            cache.price("HIT-2");
        }
        requests.clear();
        cache.price("HIT-1");
        cache.price("HIT-2");

        assertThat(requests).isEmpty();
    }

    @Test
    void cacheWithCapacityOneKeepsTheLastPrice() {
        var cache = new PriceCache(source, 1);

        cache.price("A");
        cache.price("B");
        requests.clear();
        cache.price("B");
        cache.price("A");

        assertThat(requests).containsExactly("A");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void invalidCapacityIsRejected() {
        assertThatThrownBy(() -> new PriceCache(source, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceCache(null, 10)).isInstanceOf(NullPointerException.class);
        assertThat(new PriceCache(source, 7).capacity()).isEqualTo(7);
    }
}
