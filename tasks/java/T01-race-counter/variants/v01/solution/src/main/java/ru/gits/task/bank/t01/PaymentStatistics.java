package ru.gits.task.bank.t01;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts payments processed by the processing centre.
 * {@link #record(Payment)} is called by several processing threads at the same time;
 * monitoring reads {@link #processedCount()} from its own thread.
 */
public final class PaymentStatistics {

    private final AtomicInteger processedCount = new AtomicInteger();

    /**
     * Counts a processed payment.
     *
     * @throws IllegalArgumentException if the payment amount is not positive; such payments are not counted
     */
    public void record(Payment payment) {
        Objects.requireNonNull(payment, "payment");
        if (!payment.hasPositiveAmount()) {
            throw new IllegalArgumentException("Payment " + payment.id() + " has a non-positive amount");
        }
        processedCount.incrementAndGet();
    }

    /** Number of payments counted since creation or the last {@link #reset()}. */
    public int processedCount() {
        return processedCount.get();
    }

    /** Starts a new reporting period. */
    public void reset() {
        processedCount.set(0);
    }
}
