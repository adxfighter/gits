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
     * new tariff.
     *
     * @param rater rating rules; reads the account from {@link BillingContext}; may throw for calls it
     *              cannot rate
     * @return the charge in kopecks
     */
    public long charge(CallRecord call, ToLongFunction<CallRecord> rater) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(rater, "rater");
        BillingContext.enter(call.accountId());
        try {
            var tariffChanged = new AtomicBoolean();
            Consumer<String> listener = tariffCode -> tariffChanged.set(true);
            tariffs.addListener(listener);
            try {
                long charge = rater.applyAsLong(call);
                if (tariffChanged.get()) {
                    charge = rater.applyAsLong(call);
                }
                return charge;
            } finally {
                tariffs.removeListener(listener);
            }
        } finally {
            BillingContext.leave();
        }
    }
}
