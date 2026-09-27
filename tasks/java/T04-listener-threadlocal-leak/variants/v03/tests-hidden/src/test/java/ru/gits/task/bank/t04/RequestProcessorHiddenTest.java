package ru.gits.task.bank.t04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A single-thread pool makes thread reuse between requests deterministic. */
class RequestProcessorHiddenTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final RequestProcessor processor = new RequestProcessor();

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    @Test
    void anonymousRequestAfterAClientDoesNotSeeTheClient() throws Exception {
        run(BankRequest.ofClient("r-1", "payment", "client-1"));

        Optional<String> seen = run(BankRequest.anonymous("r-2", "rates"));

        assertThat(seen).isEmpty();
        assertThat(processor.auditLog()).containsExactly("r-1 payment client-1", "r-2 rates anonymous");
    }

    @Test
    void contextIsClearedAfterAFailingHandler() throws Exception {
        var failure = pool.submit(() -> processor.handle(BankRequest.ofClient("r-1", "payment", "client-1"),
                request -> {
                    throw new IllegalStateException("payment gateway is down");
                }));
        assertThatThrownBy(() -> failure.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(run(BankRequest.anonymous("r-2", "rates"))).isEmpty();
    }

    @Test
    void nothingIsLeftInTheThreadAfterProcessing() throws Exception {
        run(BankRequest.ofClient("r-1", "payment", "client-1"));

        Optional<String> leftover = pool.submit(SecurityContext::currentClient).get(5, TimeUnit.SECONDS);

        assertThat(leftover).isEmpty();
    }

    @Test
    void mixedSequenceAlwaysSeesTheRightClient() throws Exception {
        for (int i = 0; i < 1_000; i++) {
            BankRequest request = i % 3 == 0
                    ? BankRequest.anonymous("r-" + i, "rates")
                    : BankRequest.ofClient("r-" + i, "payment", "client-" + i);

            assertThat(run(request)).as("request %d", i).isEqualTo(request.clientId());
        }
    }

    @Test
    void handlerExceptionStillReachesTheCaller() {
        assertThatThrownBy(() -> processor.handle(BankRequest.ofClient("r-1", "payment", "client-1"), request -> {
            throw new IllegalArgumentException("invalid amount");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("invalid amount");
        assertThat(SecurityContext.currentClient()).isEmpty();
    }

    /** Handles the request in the pool thread and returns the client the handler saw. */
    private Optional<String> run(BankRequest request) throws Exception {
        return pool.submit(() -> {
            Optional<String>[] seen = new Optional[1];
            processor.handle(request, r -> {
                seen[0] = SecurityContext.currentClient();
                return "ok";
            });
            return seen[0];
        }).get(5, TimeUnit.SECONDS);
    }
}
