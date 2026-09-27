package ru.gits.task.bank.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RequestProcessorVisibleTest {

    @Test
    void handlerSeesTheRequestClient() throws Exception {
        var processor = new RequestProcessor();

        String response = inFreshThread(() -> processor.handle(BankRequest.ofClient("r-1", "balance", "client-7"),
                request -> "balance of " + SecurityContext.currentClient().orElse("?")));

        assertThat(response).isEqualTo("balance of client-7");
        assertThat(processor.auditLog()).containsExactly("r-1 balance client-7");
    }

    @Test
    void anonymousRequestIsAudited() throws Exception {
        var processor = new RequestProcessor();

        inFreshThread(() -> processor.handle(BankRequest.anonymous("r-2", "rates"), request -> "rates"));

        assertThat(processor.auditLog()).containsExactly("r-2 rates anonymous");
    }

    private static <T> T inFreshThread(Callable<T> task) throws Exception {
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            return thread.submit(task).get(5, TimeUnit.SECONDS);
        } finally {
            thread.shutdownNow();
        }
    }
}
