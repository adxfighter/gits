package ru.gits.task.bank.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class CreditScoringHiddenTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    static final class ManualBureau implements CreditBureau {
        private final String name;
        final CompletableFuture<Integer> answer = new CompletableFuture<>();

        ManualBureau(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletableFuture<Integer> score(String applicantId) {
            return answer;
        }
    }

    @Test
    void silentBureauDoesNotBlockTheDecision() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var silent = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, silent), TIMEOUT, 650).decide("A-1");
        nbki.answer.complete(700);
        okb.answer.complete(660);

        assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.APPROVED,
                Map.of("nbki", 700, "okb", 660)));
    }

    @Test
    void failedBureauIsExcluded() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var equifax = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, equifax), TIMEOUT, 650).decide("A-2");
        nbki.answer.complete(600);
        okb.answer.completeExceptionally(new RuntimeException("HTTP 503"));
        equifax.answer.complete(620);

        assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.REJECTED,
                Map.of("nbki", 600, "equifax", 620)));
    }

    @Test
    void oneFailedAndOneSilentBureauMeanManualReview() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var silent = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, silent), TIMEOUT, 650).decide("A-3");
        nbki.answer.complete(800);
        okb.answer.completeExceptionally(new IllegalStateException("applicant not found"));

        assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.MANUAL_REVIEW,
                Map.of("nbki", 800)));
    }

    @Test
    void timeoutDoesNotTouchTheSharedBureauFuture() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var silent = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, silent), TIMEOUT, 650).decide("A-4");
        nbki.answer.complete(700);
        okb.answer.complete(700);
        decision.get(1, TimeUnit.SECONDS);

        assertThat(silent.answer).as("future of the bureau is shared and must stay pending").isNotDone();
        silent.answer.complete(500);
        assertThat(silent.answer.join()).isEqualTo(500);
    }

    @Test
    void answerAfterTheTimeoutIsNotCounted() throws Exception {
        var late = new ManualBureau("equifax");
        var nbki = new ManualBureau("nbki");
        nbki.answer.complete(700);
        var requested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        // The client of "okb" hands out its (never answered) future only when the test releases it,
        // so the decision is still being made when the late answer of "equifax" arrives.
        CreditBureau slowClient = new CreditBureau() {
            @Override
            public String name() {
                return "okb";
            }

            @Override
            public CompletableFuture<Integer> score(String applicantId) {
                requested.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new CompletableFuture<>();
            }
        };
        var scoring = new CreditScoring(List.of(late, nbki, slowClient), TIMEOUT, 650);

        try {
            CompletableFuture<Decision> decision = CompletableFuture
                    .supplyAsync(() -> scoring.decide("A-5"), task -> {
                        Thread caller = new Thread(task, "caller");
                        caller.setDaemon(true);
                        caller.start();
                    })
                    .thenCompose(Function.identity());
            assertThat(requested.await(1, TimeUnit.SECONDS)).as("all bureaus are requested").isTrue();

            // Delayed tasks run in trigger order on the thread that also fires completeOnTimeout/orTimeout,
            // so "equifax" answers 150 ms after the request, well after its 100 ms timeout.
            CompletableFuture.runAsync(() -> late.answer.complete(300),
                    CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS, Runnable::run))
                    .get(1, TimeUnit.SECONDS);
            release.countDown();

            assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.MANUAL_REVIEW,
                    Map.of("nbki", 700)));
        } finally {
            release.countDown();
        }
    }

    @Test
    void allBureausSilentMeansManualReview() throws Exception {
        var decision = new CreditScoring(
                List.of(new ManualBureau("nbki"), new ManualBureau("okb"), new ManualBureau("equifax")),
                TIMEOUT, 650).decide("A-6");

        assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.MANUAL_REVIEW, Map.of()));
    }

    @Test
    void bureauThrowingRightAwayIsExcluded() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        CreditBureau broken = new CreditBureau() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public CompletableFuture<Integer> score(String applicantId) {
                throw new IllegalStateException("certificate expired");
            }
        };
        nbki.answer.complete(700);
        okb.answer.complete(680);

        var decision = new CreditScoring(List.of(nbki, broken, okb), TIMEOUT, 650).decide("A-7");

        assertThat(decision.get(1, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.APPROVED,
                Map.of("nbki", 700, "okb", 680)));
    }
}
