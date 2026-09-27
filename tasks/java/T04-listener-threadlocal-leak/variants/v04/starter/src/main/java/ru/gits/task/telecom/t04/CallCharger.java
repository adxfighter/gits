package ru.gits.task.telecom.t04;

import java.util.Objects;
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
        BillingContext.enter(call.accountId());
        var tariffChanged = new AtomicBoolean();
        Consumer<String> listener = tariffCode -> tariffChanged.set(true);
        tariffs.addListener(listener);

        long charge = rate(call, rater, tariffChanged);

        tariffs.removeListener(listener);
        BillingContext.leave();
        return charge;
    }

    private static long rate(CallRecord call, ToLongFunction<CallRecord> rater, AtomicBoolean tariffChanged) {
        long charge;
        try {
            charge = rater.applyAsLong(call);
        } catch (RuntimeException failure) {
            if (!tariffChanged.get()) {
                throw failure;
            }
            // the error may be caused by the old tariff: rate once more with the new one
            return rater.applyAsLong(call);
        }
        return tariffChanged.get() ? rater.applyAsLong(call) : charge;
    }
}
