package ru.gits.task.telecom.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NumberCheckHiddenTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);
    private static final String POOL_THREAD = "check-pool";

    private final ExecutorService pool = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, POOL_THREAD);
        thread.setDaemon(true);
        return thread;
    });

    /** Counts the tasks submitted to the service's executor. */
    private final AtomicInteger submitted = new AtomicInteger();
    private final Executor countingExecutor = task -> {
        submitted.incrementAndGet();
        pool.execute(task);
    };

    /** Threads that invoked the stubs. */
    private final List<String> callerThreads = new CopyOnWriteArrayList<>();

    private final CompletableFuture<String> operatorAnswer = new CompletableFuture<>();
    private final CompletableFuture<Boolean> fraudAnswer = new CompletableFuture<>();

    private final NumberRegistry registry = msisdn -> {
        callerThreads.add(Thread.currentThread().getName());
        return operatorAnswer;
    };
    private final FraudService fraud = (operator, msisdn) -> {
        callerThreads.add(Thread.currentThread().getName());
        return fraudAnswer;
    };

    @AfterEach
    void release() {
        operatorAnswer.complete("MTS");
        fraudAnswer.complete(false);
        pool.shutdownNow();
    }

    private CompletableFuture<Verdict> verify(String number) {
        return new NumberCheck(registry, fraud, countingExecutor, TIMEOUT).verify(number);
    }

    @Test
    void normalizationRunsOnTheGivenExecutor() throws Exception {
        operatorAnswer.complete("BEELINE");
        fraudAnswer.complete(false);

        assertThat(verify("8 912 345 67 89").get(2, TimeUnit.SECONDS))
                .isEqualTo(new Verdict("79123456789", "BEELINE", false));
        assertThat(submitted.get()).as("tasks submitted to the service executor").isPositive();
    }

    @Test
    void dependentCallsRunOnlyOnTheExecutorOrTheAnsweringThread() throws Exception {
        String testThread = Thread.currentThread().getName();

        CompletableFuture<Verdict> verdict = verify("+79123456789");
        awaitCalls(1);
        operatorAnswer.complete("MEGAFON");
        awaitCalls(2);
        fraudAnswer.complete(true);

        assertThat(verdict.get(2, TimeUnit.SECONDS)).isEqualTo(new Verdict("79123456789", "MEGAFON", true));
        assertThat(callerThreads).allSatisfy(thread -> assertThat(thread).isIn(testThread, POOL_THREAD));
    }

    @Test
    void silentRegistryTimesOut() {
        CompletableFuture<Verdict> verdict = verify("+79123456789");

        assertThat(verdict).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void silentFraudServiceTimesOut() {
        operatorAnswer.complete("TELE2");

        CompletableFuture<Verdict> verdict = verify("+79123456789");

        assertThat(verdict).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void eachStepHasItsOwnTimeout() throws Exception {
        CompletableFuture<Verdict> verdict = verify("+79123456789");
        awaitCalls(1);
        Thread.sleep(55);
        operatorAnswer.complete("MTS");
        awaitCalls(2);
        Thread.sleep(55);
        fraudAnswer.complete(true);

        assertThat(verdict.get(2, TimeUnit.SECONDS)).isEqualTo(new Verdict("79123456789", "MTS", true));
    }

    @Test
    void registryFailureReachesTheCaller() {
        operatorAnswer.completeExceptionally(new IllegalStateException("registry is down"));

        assertThat(verify("+79123456789")).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("registry is down");
    }

    private void awaitCalls(int calls) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (callerThreads.size() < calls && System.nanoTime() < deadline) {
            Thread.sleep(2);
        }
        assertThat(callerThreads).as("stub calls").hasSizeGreaterThanOrEqualTo(calls);
    }
}
