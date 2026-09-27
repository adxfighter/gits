package ru.gits.task.logistics.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RateShoppingVisibleTest {

    /** Carrier stub: the test completes the answer by hand. */
    static final class ManualCarrier implements CarrierClient {
        private final String name;
        final CompletableFuture<BigDecimal> answer = new CompletableFuture<>();

        ManualCarrier(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<BigDecimal> rate(int weightGrams) {
            return answer;
        }
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "app-pool");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void ratesOfAllCarriersCheapestFirst() throws Exception {
        var cdek = new ManualCarrier("cdek");
        var boxberry = new ManualCarrier("boxberry");
        var post = new ManualCarrier("post");

        var result = new RateShopping(List.of(cdek, boxberry, post), pool).quotes(1_500);
        post.answer.complete(new BigDecimal("310"));
        cdek.answer.complete(new BigDecimal("450"));
        boxberry.answer.complete(new BigDecimal("290"));

        assertThat(result.get(2, TimeUnit.SECONDS)).containsExactly(
                new CarrierRate("boxberry", new BigDecimal("290")),
                new CarrierRate("post", new BigDecimal("310")),
                new CarrierRate("cdek", new BigDecimal("450")));
    }

    @Test
    void equalPricesAreOrderedByCarrierName() throws Exception {
        var post = new ManualCarrier("post");
        var cdek = new ManualCarrier("cdek");

        var result = new RateShopping(List.of(post, cdek), pool).quotes(500);
        post.answer.complete(new BigDecimal("200"));
        cdek.answer.complete(new BigDecimal("200"));

        assertThat(result.get(2, TimeUnit.SECONDS)).extracting(CarrierRate::carrier).containsExactly("cdek", "post");
    }
}
