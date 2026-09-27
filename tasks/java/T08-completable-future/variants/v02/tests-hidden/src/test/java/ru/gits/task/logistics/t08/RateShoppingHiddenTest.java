package ru.gits.task.logistics.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RateShoppingHiddenTest {

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

    private final List<ManualCarrier> created = new ArrayList<>();

    private final ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "app-pool");
        thread.setDaemon(true);
        return thread;
    });

    private ManualCarrier carrier(String name) {
        var carrier = new ManualCarrier(name);
        created.add(carrier);
        return carrier;
    }

    @AfterEach
    void releaseEverything() {
        created.forEach(carrier -> carrier.answer.complete(BigDecimal.ONE));
        pool.shutdownNow();
    }

    @Test
    void failedCarrierDoesNotHideOtherRates() throws Exception {
        var cdek = carrier("cdek");
        var boxberry = carrier("boxberry");
        var post = carrier("post");

        var result = new RateShopping(List.of(cdek, boxberry, post), pool).quotes(1_000);
        cdek.answer.complete(new BigDecimal("450"));
        boxberry.answer.completeExceptionally(new RuntimeException("HTTP 502"));
        post.answer.complete(new BigDecimal("310"));

        assertThat(result.get(2, TimeUnit.SECONDS)).containsExactly(
                new CarrierRate("post", new BigDecimal("310")),
                new CarrierRate("cdek", new BigDecimal("450")));
    }

    @Test
    void allCarriersFailedGivesAnEmptyList() throws Exception {
        var cdek = carrier("cdek");
        var post = carrier("post");

        var result = new RateShopping(List.of(cdek, post), pool).quotes(1_000);
        cdek.answer.completeExceptionally(new RuntimeException("timeout"));
        post.answer.completeExceptionally(new RuntimeException("HTTP 500"));

        assertThat(result.get(2, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    void waitingForCarriersDoesNotOccupyThePool() throws Exception {
        var cdek = carrier("cdek");
        var boxberry = carrier("boxberry");
        var post = carrier("post");

        var result = new RateShopping(List.of(cdek, boxberry, post), pool).quotes(1_000);

        CountDownLatch otherTaskRan = new CountDownLatch(1);
        pool.execute(otherTaskRan::countDown);
        assertThat(otherTaskRan.await(1, TimeUnit.SECONDS))
                .as("another task of the application pool runs while carriers are silent")
                .isTrue();

        cdek.answer.complete(new BigDecimal("1"));
        boxberry.answer.complete(new BigDecimal("2"));
        post.answer.complete(new BigDecimal("3"));
        assertThat(result.get(2, TimeUnit.SECONDS)).hasSize(3);
    }

    @Test
    void resultWaitsForTheSlowestCarrier() {
        var cdek = carrier("cdek");
        var post = carrier("post");

        var result = new RateShopping(List.of(cdek, post), pool).quotes(1_000);
        cdek.answer.completeExceptionally(new RuntimeException("HTTP 502"));

        assertThat(result).isNotDone();
        post.answer.complete(new BigDecimal("310"));
        assertThat(result).succeedsWithin(2, TimeUnit.SECONDS)
                .isEqualTo(List.of(new CarrierRate("post", new BigDecimal("310"))));
    }

    @Test
    void manyCarriersSomeFailing() throws Exception {
        List<CarrierClient> carriers = new ArrayList<>();
        List<ManualCarrier> manual = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var carrier = carrier("c" + i);
            carriers.add(carrier);
            manual.add(carrier);
        }

        var result = new RateShopping(carriers, pool).quotes(2_000);
        for (int i = 7; i >= 0; i--) {
            if (i % 3 == 0) {
                manual.get(i).answer.completeExceptionally(new RuntimeException("down"));
            } else {
                manual.get(i).answer.complete(BigDecimal.valueOf(100 - i));
            }
        }

        assertThat(result.get(2, TimeUnit.SECONDS)).extracting(CarrierRate::carrier)
                .containsExactly("c7", "c5", "c4", "c2", "c1");
    }
}
