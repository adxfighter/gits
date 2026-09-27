package ru.gits.task.telecom.t04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A single-thread pool makes thread reuse deterministic. */
class CallChargerHiddenTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final TariffCatalog tariffs = new TariffCatalog();
    private final CallCharger charger = new CallCharger(tariffs);

    private static final ToLongFunction<CallRecord> UNRATABLE = call -> {
        throw new IllegalStateException("No tariff for " + call.callee());
    };

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void failedRatingsDoNotLeaveListeners() throws Exception {
        for (int i = 0; i < 1_000; i++) {
            chargeInPool(call("c-" + i, "acc-" + i), UNRATABLE);
        }

        assertThat(tariffs.listenerCount()).isZero();
    }

    @Test
    void failedRatingDoesNotLeaveTheAccountInTheThread() throws Exception {
        chargeInPool(call("c-1", "acc-1"), UNRATABLE);

        Optional<String> leftover = pool.submit(BillingContext::currentAccount).get(5, TimeUnit.SECONDS);

        assertThat(leftover).isEmpty();
    }

    @Test
    void nextCallSeesOnlyItsOwnAccount() throws Exception {
        chargeInPool(call("c-1", "acc-1"), UNRATABLE);

        long charge = pool.submit(() -> charger.charge(call("c-2", "acc-2"),
                c -> BillingContext.currentAccount().orElseThrow().equals("acc-2") ? 10 : -1))
                .get(5, TimeUnit.SECONDS);

        assertThat(charge).isEqualTo(10);
    }

    @Test
    void ratingErrorReachesTheCallerUnchanged() {
        assertThatThrownBy(() -> charger.charge(call("c-1", "acc-1"), UNRATABLE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No tariff for 79001112233");
        assertThat(BillingContext.currentAccount()).isEmpty();
        assertThat(tariffs.listenerCount()).isZero();
    }

    @Test
    void errorOnTheSecondRatingAlsoReleasesEverything() throws Exception {
        int[] ratings = {0};
        ToLongFunction<CallRecord> failsAfterTariffChange = call -> {
            if (++ratings[0] == 1) {
                tariffs.tariffChanged("SMART-5");
                return 100;
            }
            throw new IllegalStateException("Tariff SMART-5 was withdrawn");
        };

        chargeInPool(call("c-1", "acc-1"), failsAfterTariffChange);

        assertThat(tariffs.listenerCount()).isZero();
        assertThat(pool.submit(BillingContext::currentAccount).get(5, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    void successfulChargesStillReleaseEverything() throws Exception {
        for (int i = 0; i < 100; i++) {
            CallRecord record = call("c-" + i, "acc-" + i);
            long charge = pool.submit(() -> charger.charge(record, c -> c.seconds())).get(5, TimeUnit.SECONDS);
            assertThat(charge).isEqualTo(60);
        }

        assertThat(tariffs.listenerCount()).isZero();
        assertThat(pool.submit(BillingContext::currentAccount).get(5, TimeUnit.SECONDS)).isEmpty();
    }

    private static CallRecord call(String callId, String accountId) {
        return new CallRecord(callId, accountId, "79001112233", 60);
    }

    private void chargeInPool(CallRecord record, ToLongFunction<CallRecord> rater) throws Exception {
        var future = pool.submit(() -> charger.charge(record, rater));
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }
}
