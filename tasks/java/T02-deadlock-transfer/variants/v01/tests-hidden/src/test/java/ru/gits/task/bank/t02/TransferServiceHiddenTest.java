package ru.gits.task.bank.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class TransferServiceHiddenTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final TransferService service = new TransferService();

    @Test
    void oppositeTransfersFinishInTime() {
        var alice = new Account(1, 1_000_000);
        var bob = new Account(2, 1_000_000);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 50_000; i++) {
                if (thread % 2 == 0) {
                    service.transfer(alice, bob, 1);
                } else {
                    service.transfer(bob, alice, 1);
                }
            }
        }), "opposite transfers must not hang");

        assertThat(alice.balance()).isEqualTo(1_000_000);
        assertThat(bob.balance()).isEqualTo(1_000_000);
    }

    @Test
    void moneyIsConservedAcrossManyAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            accounts.add(new Account(100 + i, 1_000_000));
        }

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(8, thread -> {
            var random = new Random(thread);
            for (int i = 0; i < 20_000; i++) {
                Account from = accounts.get(random.nextInt(accounts.size()));
                Account to = accounts.get(random.nextInt(accounts.size()));
                if (from != to) {
                    service.transfer(from, to, 1 + random.nextInt(10));
                }
            }
        }));

        assertThat(accounts.stream().mapToLong(Account::balance).sum()).isEqualTo(10_000_000);
        assertThat(accounts).allMatch(account -> account.balance() >= 0);
    }

    @Test
    void failedTransfersUnderLoadChangeNothing() {
        var poor = new Account(1, 10);
        var rich = new Account(2, 1_000_000);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 20_000; i++) {
                try {
                    if (thread % 2 == 0) {
                        service.transfer(poor, rich, 1_000);
                    } else {
                        service.transfer(rich, poor, 1);
                        service.transfer(poor, rich, 1);
                    }
                } catch (IllegalStateException expected) {
                    // insufficient funds
                }
            }
        }));

        assertThat(poor.balance() + rich.balance()).isEqualTo(1_000_010);
        assertThat(poor.balance()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        var alice = new Account(1, 100);

        assertThatThrownBy(() -> service.transfer(alice, alice, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThat(alice.balance()).isEqualTo(100);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        var alice = new Account(1, 100);
        var bob = new Account(2, 100);

        assertThatThrownBy(() -> service.transfer(alice, bob, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transfer(alice, bob, -5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(alice.balance()).isEqualTo(100);
        assertThat(bob.balance()).isEqualTo(100);
    }

    @FunctionalInterface
    private interface ThreadWork {
        void run(int threadNo) throws Exception;
    }

    /** Worker threads are daemons, so a deadlocked run cannot keep the JVM alive. */
    private static void runConcurrently(int threads, ThreadWork work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int threadNo = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    work.run(threadNo);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
