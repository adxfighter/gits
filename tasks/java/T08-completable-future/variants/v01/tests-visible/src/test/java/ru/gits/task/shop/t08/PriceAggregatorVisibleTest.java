package ru.gits.task.shop.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PriceAggregatorVisibleTest {

    /** Supplier stub: the test completes the answer by hand. */
    static final class ManualSupplier implements SupplierClient {
        private final String name;
        final CompletableFuture<BigDecimal> answer = new CompletableFuture<>();

        ManualSupplier(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<BigDecimal> quote(String sku) {
            return answer;
        }
    }

    @Test
    void bestPriceOfTwoSuppliers() throws Exception {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        alpha.answer.complete(new BigDecimal("120.00"));
        beta.answer.complete(new BigDecimal("99.90"));

        assertThat(offer.get(2, TimeUnit.SECONDS)).isEqualTo(new PriceOffer("MUG-1",
                Map.of("alpha", new BigDecimal("120.00"), "beta", new BigDecimal("99.90")),
                Optional.of(new BigDecimal("99.90"))));
    }

    @Test
    void offerWaitsForBothSuppliers() {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        alpha.answer.complete(new BigDecimal("10"));

        assertThat(offer).isNotDone();
        beta.answer.complete(new BigDecimal("11"));
        assertThat(offer).isCompleted();
    }
}
