package ru.gits.task.telecom.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NumberCheckVisibleTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    private final ExecutorService pool = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "check-pool");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void numberIsNormalizedAndChecked() throws Exception {
        NumberRegistry registry = msisdn -> CompletableFuture.completedFuture(msisdn.equals("79123456789") ? "MTS" : "?");
        FraudService fraud = (operator, msisdn) -> CompletableFuture.completedFuture(operator.equals("MTS"));

        var verdict = new NumberCheck(registry, fraud, pool, TIMEOUT).verify("+7 (912) 345-67-89");

        assertThat(verdict.get(2, TimeUnit.SECONDS)).isEqualTo(new Verdict("79123456789", "MTS", true));
    }

    @Test
    void invalidNumberFailsTheCheck() {
        NumberRegistry registry = msisdn -> CompletableFuture.completedFuture("MTS");
        FraudService fraud = (operator, msisdn) -> CompletableFuture.completedFuture(false);

        var verdict = new NumberCheck(registry, fraud, pool, TIMEOUT).verify("12-34");

        assertThat(verdict).failsWithin(2, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }
}
