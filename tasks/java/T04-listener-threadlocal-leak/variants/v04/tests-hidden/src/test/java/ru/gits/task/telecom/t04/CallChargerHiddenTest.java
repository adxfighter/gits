package ru.gits.task.telecom.t04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void forwardedLegDoesNotTakeTheAccountAwayFromTheOuterCall() throws Exception {
        List<String> seenByOuterRules = new ArrayList<>();
        CallRecord forwardedLeg = call("c-1/leg", "acc-forward");

        long charge = pool.submit(() -> charger.charge(call("c-1", "acc-1"), outer -> {
            long leg = charger.charge(forwardedLeg, inner -> 30);
            seenByOuterRules.add(BillingContext.currentAccount().orElse("none"));
            return 100 + leg;
        })).get(5, TimeUnit.SECONDS);

        assertThat(charge).isEqualTo(130);
        assertThat(seenByOuterRules).containsExactly("acc-1");
        assertThat(tariffs.listenerCount()).isZero();
        assertThat(pool.submit(BillingContext::currentAccount).get(5, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    void failedForwardedLegRestoresTheOuterAccount() {
        List<String> seenByOuterRules = new ArrayList<>();

        long charge = charger.charge(call("c-1", "acc-1"), outer -> {
            long leg;
            try {
                leg = charger.charge(call("c-1/leg", "acc-forward"), UNRATABLE);
            } catch (IllegalStateException unratableLeg) {
                leg = 0;
            }
            seenByOuterRules.add(BillingContext.currentAccount().orElse("none"));
            return 100 + leg;
        });

        assertThat(charge).isEqualTo(100);
        assertThat(seenByOuterRules).containsExactly("acc-1");
        assertThat(BillingContext.currentAccount()).isEmpty();
        assertThat(tariffs.listenerCount()).isZero();
    }

    @Test
    void doublyNestedLegsRestoreEachLevel() {
        List<String> seen = new ArrayList<>();

        charger.charge(call("c-1", "acc-1"), first -> {
            charger.charge(call("c-2", "acc-2"), second -> {
                charger.charge(call("c-3", "acc-3"), third -> 1);
                seen.add(BillingContext.currentAccount().orElse("none"));
                return 1;
            });
            seen.add(BillingContext.currentAccount().orElse("none"));
            return 1;
        });

        assertThat(seen).containsExactly("acc-2", "acc-1");
        assertThat(BillingContext.currentAccount()).isEmpty();
    }

    @Test
    void failedReRatingDoesNotMaskTheOriginalError() {
        int[] ratings = {0};
        ToLongFunction<CallRecord> failsTwice = call -> {
            if (++ratings[0] == 1) {
                tariffs.tariffChanged("SMART-5");
                throw new IllegalStateException("No roaming zone for 79001112233");
            }
            throw new IllegalArgumentException("Tariff SMART-5 was withdrawn");
        };

        Throwable thrown = catchThrowable(() -> charger.charge(call("c-1", "acc-1"), failsTwice));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("No roaming zone for 79001112233");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tariff SMART-5 was withdrawn");
        assertThat(ratings[0]).isEqualTo(2);
        assertThat(BillingContext.currentAccount()).isEmpty();
        assertThat(tariffs.listenerCount()).isZero();
    }

    @Test
    void sameErrorFromBothRatingsIsRethrownAsIs() {
        var withdrawn = new IllegalStateException("Tariff SMART-5 was withdrawn");
        int[] ratings = {0};
        ToLongFunction<CallRecord> alwaysWithdrawn = call -> {
            if (++ratings[0] == 1) {
                tariffs.tariffChanged("SMART-5");
            }
            throw withdrawn;
        };

        Throwable thrown = catchThrowable(() -> charger.charge(call("c-1", "acc-1"), alwaysWithdrawn));

        assertThat(thrown).isSameAs(withdrawn);
        assertThat(thrown.getSuppressed()).isEmpty();
        assertThat(tariffs.listenerCount()).isZero();
    }

    private static CallRecord call(String callId, String accountId) {
        return new CallRecord(callId, accountId, "79001112233", 60);
    }

    private void chargeInPool(CallRecord record, ToLongFunction<CallRecord> rater) throws Exception {
        var future = pool.submit(() -> charger.charge(record, rater));
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }
}
