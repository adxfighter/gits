package ru.gits.task.bank.t08;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Credit decision based on the scores of several bureaus.
 */
public final class CreditScoring {

    private static final int MIN_SCORES = 2;

    private final List<CreditBureau> bureaus;
    private final Duration bureauTimeout;
    private final int approvalThreshold;

    public CreditScoring(List<CreditBureau> bureaus, Duration bureauTimeout, int approvalThreshold) {
        this.bureaus = List.copyOf(bureaus);
        this.bureauTimeout = Objects.requireNonNull(bureauTimeout, "bureauTimeout");
        this.approvalThreshold = approvalThreshold;
    }

    public CompletableFuture<Decision> decide(String applicantId) {
        List<CompletableFuture<Integer>> answers = bureaus.stream()
                .map(bureau -> bureau.score(applicantId))
                .toList();

        return CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, Integer> scores = new HashMap<>();
                    for (int i = 0; i < bureaus.size(); i++) {
                        scores.put(bureaus.get(i).name(), answers.get(i).join());
                    }
                    return decision(scores);
                });
    }

    private Decision decision(Map<String, Integer> scores) {
        if (scores.size() < MIN_SCORES) {
            return new Decision(Decision.Outcome.MANUAL_REVIEW, scores);
        }
        double average = scores.values().stream().mapToInt(Integer::intValue).average().orElseThrow();
        Decision.Outcome outcome = average >= approvalThreshold ? Decision.Outcome.APPROVED : Decision.Outcome.REJECTED;
        return new Decision(outcome, scores);
    }
}
