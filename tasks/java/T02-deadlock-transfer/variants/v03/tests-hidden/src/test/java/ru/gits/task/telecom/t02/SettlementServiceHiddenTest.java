package ru.gits.task.telecom.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class SettlementServiceHiddenTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    @Test
    void oppositeSettlementsFinishInTime() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        var alpha = new OperatorAccount(1, "Альфа", 1_000_000_000);
        var beta = new OperatorAccount(2, "Бета", 1_000_000_000);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 30_000; i++) {
                if (thread % 2 == 0) {
                    service.settle(alpha, beta, 1_000);
                } else {
                    service.settle(beta, alpha, 1_000);
                }
            }
        }), "opposite settlements must not hang");

        assertThat(alpha.balance() + beta.balance() + clearing.balance()).isEqualTo(2_000_000_000L);
        assertThat(clearing.balance()).isEqualTo(4 * 30_000 * SettlementService.feeOf(1_000));
    }

    @Test
    void settlementsAcrossThreeOperatorsFinishAndConserveMoney() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        List<OperatorAccount> operators = List.of(
                new OperatorAccount(3, "Гамма", 500_000_000),
                new OperatorAccount(1, "Альфа", 500_000_000),
                new OperatorAccount(2, "Бета", 500_000_000));

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(6, thread -> {
            OperatorAccount payer = operators.get(thread % 3);
            OperatorAccount payee = operators.get((thread + 1 + thread / 3) % 3);
            for (int i = 0; i < 20_000; i++) {
                service.settle(payer, payee, 200);
            }
        }));

        long operatorsTotal = operators.stream().mapToLong(OperatorAccount::balance).sum();
        assertThat(operatorsTotal + clearing.balance()).isEqualTo(1_500_000_000L);
        assertThat(clearing.balance()).isEqualTo(6 * 20_000 * SettlementService.feeOf(200));
    }

    @Test
    void failedSettlementsUnderLoadChangeNothing() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        var poor = new OperatorAccount(1, "Малый", 0);
        var rich = new OperatorAccount(2, "Крупный", 1_000_000_000);

        assertTimeoutPreemptively(DEADLINE, () -> runConcurrently(4, thread -> {
            for (int i = 0; i < 20_000; i++) {
                try {
                    if (thread % 2 == 0) {
                        service.settle(poor, rich, 1_000_000);
                    } else {
                        service.settle(rich, poor, 100);
                    }
                } catch (IllegalStateException expected) {
                    // payer cannot pay
                }
            }
        }));

        assertThat(poor.balance() + rich.balance() + clearing.balance()).isEqualTo(1_000_000_000L);
    }

    @Test
    void failedSettlementDoesNotChargeTheClearingFee() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        var alpha = new OperatorAccount(1, "Альфа", 100);
        var beta = new OperatorAccount(2, "Бета", 0);

        assertThatThrownBy(() -> service.settle(alpha, beta, 1_000)).isInstanceOf(IllegalStateException.class);

        assertThat(alpha.balance()).isEqualTo(100);
        assertThat(beta.balance()).isZero();
        assertThat(clearing.balance()).isZero();
    }

    @Test
    void rejectsInvalidSettlements() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        var alpha = new OperatorAccount(1, "Альфа", 100);

        assertThatThrownBy(() -> service.settle(alpha, alpha, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.settle(alpha, new OperatorAccount(1, "Альфа-2", 0), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.settle(alpha, new OperatorAccount(2, "Бета", 0), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(alpha.balance()).isEqualTo(100);
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
