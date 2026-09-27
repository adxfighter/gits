package ru.gits.task.shop.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PriceAggregatorHiddenTest {

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

    static final class BrokenClientSupplier implements SupplierClient {
        @Override
        public String name() {
            return "broken";
        }

        @Override
        public CompletableFuture<BigDecimal> quote(String sku) {
            throw new IllegalStateException("connection pool exhausted");
        }
    }

    @Test
    void failureOfTheFirstSupplierKeepsTheSecondPrice() throws Exception {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        alpha.answer.completeExceptionally(new RuntimeException("HTTP 503"));
        beta.answer.complete(new BigDecimal("99.90"));

        assertThat(offer.get(2, TimeUnit.SECONDS)).isEqualTo(new PriceOffer("MUG-1",
                Map.of("beta", new BigDecimal("99.90")), Optional.of(new BigDecimal("99.90"))));
    }

    @Test
    void failureOfTheSecondSupplierKeepsTheFirstPrice() throws Exception {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        beta.answer.completeExceptionally(new RuntimeException("HTTP 500"));
        alpha.answer.complete(new BigDecimal("120.00"));

        assertThat(offer.get(2, TimeUnit.SECONDS)).isEqualTo(new PriceOffer("MUG-1",
                Map.of("alpha", new BigDecimal("120.00")), Optional.of(new BigDecimal("120.00"))));
    }

    @Test
    void bothSuppliersFailedGivesAnEmptyOffer() throws Exception {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        alpha.answer.completeExceptionally(new RuntimeException("HTTP 503"));
        beta.answer.completeExceptionally(new RuntimeException("HTTP 503"));

        assertThat(offer.get(2, TimeUnit.SECONDS)).isEqualTo(new PriceOffer("MUG-1", Map.of(), Optional.empty()));
    }

    @Test
    void supplierThrowingFromQuoteIsTreatedAsAFailure() throws Exception {
        var beta = new ManualSupplier("beta");
        beta.answer.complete(new BigDecimal("75.50"));

        CompletableFuture<PriceOffer> offer = new PriceAggregator(new BrokenClientSupplier(), beta).offer("MUG-1");

        assertThat(offer.get(2, TimeUnit.SECONDS)).isEqualTo(new PriceOffer("MUG-1",
                Map.of("beta", new BigDecimal("75.50")), Optional.of(new BigDecimal("75.50"))));
    }

    @Test
    void failedSupplierStillLetsTheOfferWaitForTheOther() {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        alpha.answer.completeExceptionally(new RuntimeException("HTTP 503"));

        assertThat(offer).isNotDone();
        beta.answer.complete(new BigDecimal("1"));
        assertThat(offer).isCompletedWithValueMatching(result -> result.quotes().size() == 1);
    }

    @Test
    void lowestPriceWinsWhateverTheAnswerOrder() throws Exception {
        var alpha = new ManualSupplier("alpha");
        var beta = new ManualSupplier("beta");

        CompletableFuture<PriceOffer> offer = new PriceAggregator(alpha, beta).offer("MUG-1");
        beta.answer.complete(new BigDecimal("50.00"));
        alpha.answer.complete(new BigDecimal("49.99"));

        assertThat(offer.get(2, TimeUnit.SECONDS).bestPrice()).contains(new BigDecimal("49.99"));
    }
}
