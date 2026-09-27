package ru.gits.task.telecom.t08;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Number check before porting: normalization, current operator, anti-fraud flag.
 */
public final class NumberCheck {

    private final NumberRegistry registry;
    private final FraudService fraud;
    private final Executor executor;
    private final Duration stepTimeout;

    public NumberCheck(NumberRegistry registry, FraudService fraud, Executor executor, Duration stepTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fraud = Objects.requireNonNull(fraud, "fraud");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.stepTimeout = Objects.requireNonNull(stepTimeout, "stepTimeout");
    }

    public CompletableFuture<Verdict> verify(String rawNumber) {
        return CompletableFuture.supplyAsync(() -> normalize(rawNumber))
                .thenApply(msisdn -> {
                    String operator = registry.operatorOf(msisdn).join();
                    CompletableFuture<Boolean> flagged = fraud.isFlagged(operator, msisdn);
                    return new Verdict(msisdn, operator, flagged.join());
                });
    }

    /** Normalizes +7 (912) 345-67-89, 8 912 345 67 89 and similar to 79123456789. */
    static String normalize(String rawNumber) {
        Objects.requireNonNull(rawNumber, "rawNumber");
        String digits = rawNumber.replaceAll("[\\s()\\-]", "");
        if (digits.startsWith("+7")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("8") && digits.length() == 11) {
            digits = "7" + digits.substring(1);
        }
        if (!digits.matches("7\\d{10}")) {
            throw new IllegalArgumentException("Not a Russian mobile number: " + rawNumber);
        }
        return digits;
    }
}
