package ru.gits.task.bank.t08;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CreditScoringVisibleTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);

    /** Bureau stub: answers when the test completes the future. */
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
    void allBureausAnsweredWithAHighAverage() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var equifax = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, equifax), TIMEOUT, 650).decide("A-1");
        nbki.answer.complete(700);
        okb.answer.complete(640);
        equifax.answer.complete(680);

        assertThat(decision.get(2, TimeUnit.SECONDS)).isEqualTo(new Decision(Decision.Outcome.APPROVED,
                Map.of("nbki", 700, "okb", 640, "equifax", 680)));
    }

    @Test
    void allBureausAnsweredWithALowAverage() throws Exception {
        var nbki = new ManualBureau("nbki");
        var okb = new ManualBureau("okb");
        var equifax = new ManualBureau("equifax");

        var decision = new CreditScoring(List.of(nbki, okb, equifax), TIMEOUT, 650).decide("A-2");
        nbki.answer.complete(600);
        okb.answer.complete(610);
        equifax.answer.complete(720);

        assertThat(decision.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(Decision.Outcome.REJECTED);
    }
}
