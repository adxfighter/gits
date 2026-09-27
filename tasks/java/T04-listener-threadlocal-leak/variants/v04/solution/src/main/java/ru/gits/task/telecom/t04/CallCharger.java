package ru.gits.task.telecom.t04;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * Charges completed calls. Runs in the billing thread pool; threads are reused between calls.
 */
public final class CallCharger {

    private final TariffCatalog tariffs;

    public CallCharger(TariffCatalog tariffs) {
        this.tariffs = Objects.requireNonNull(tariffs, "tariffs");
    }

    /**
     * Charges a call. If a tariff changes while the call is being rated, it is rated once more with the
     * new tariff, whether the first rating succeeded or failed. Rating rules of a forwarded call charge
     * the forwarded leg by calling this method again from inside the rater.
     *
     * @param rater rating rules; reads the account from {@link BillingContext}; may throw for calls it
     *              cannot rate
     * @return the charge in kopecks
     */
    public long charge(CallRecord call, ToLongFunction<CallRecord> rater) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(rater, "rater");
        // a nested charge (forwarded leg) must give the outer charge its account back
        Optional<String> outerAccount = BillingContext.currentAccount();
        BillingContext.enter(call.accountId());
        try {
            var tariffChanged = new AtomicBoolean();
            Consumer<String> listener = tariffCode -> tariffChanged.set(true);
            tariffs.addListener(listener);
            try {
                return rate(call, rater, tariffChanged);
            } finally {
                tariffs.removeListener(listener);
            }
        } finally {
            outerAccount.ifPresentOrElse(BillingContext::enter, BillingContext::leave);
        }
    }

    private static long rate(CallRecord call, ToLongFunction<CallRecord> rater, AtomicBoolean tariffChanged) {
        long charge;
        try {
            charge = rater.applyAsLong(call);
        } catch (RuntimeException firstFailure) {
            if (!tariffChanged.get()) {
                throw firstFailure;
            }
            try {
                return rater.applyAsLong(call);
            } catch (RuntimeException secondFailure) {
                // the original cause must reach the caller; the second error is kept as suppressed
                if (secondFailure != firstFailure) {
                    firstFailure.addSuppressed(secondFailure);
                }
                throw firstFailure;
            }
        }
        return tariffChanged.get() ? rater.applyAsLong(call) : charge;
    }
}
