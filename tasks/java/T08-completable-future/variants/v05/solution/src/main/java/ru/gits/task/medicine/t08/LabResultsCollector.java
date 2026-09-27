package ru.gits.task.medicine.t08;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Collects analysis results of a referral from all partner laboratories.
 */
public final class LabResultsCollector {

    private static final String TIMEOUT = "timeout";

    private final List<Lab> labs;
    private final Executor executor;
    private final Duration labTimeout;

    public LabResultsCollector(List<Lab> labs, Executor executor, Duration labTimeout) {
        this.labs = List.copyOf(labs);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.labTimeout = Objects.requireNonNull(labTimeout, "labTimeout");
    }

    public CompletableFuture<LabReport> collect(String orderId) {
        List<CompletableFuture<Answer>> answers = labs.stream()
                .map(lab -> answer(lab, orderId))
                .toList();

        // The report is assembled in a continuation: no pool thread waits for the laboratories.
        return CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> {
                    Map<String, LabResult> results = new HashMap<>();
                    Map<String, String> failures = new HashMap<>();
                    for (int i = 0; i < labs.size(); i++) {
                        String name = labs.get(i).name();
                        Answer answer = answers.get(i).join();
                        if (answer.result() != null) {
                            results.put(name, answer.result());
                        } else {
                            failures.put(name, answer.failure());
                        }
                    }
                    return new LabReport(results, failures);
                }, executor);
    }

    private CompletableFuture<Answer> answer(Lab lab, String orderId) {
        try {
            return lab.result(orderId)
                    .thenApply(Answer::of)
                    .completeOnTimeout(Answer.failed(TIMEOUT), labTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(failure -> Answer.failed(reason(failure)));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(Answer.failed(reason(failure)));
        }
    }

    private static String reason(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private record Answer(LabResult result, String failure) {

        static Answer of(LabResult result) {
            return new Answer(result, null);
        }

        static Answer failed(String failure) {
            return new Answer(null, failure);
        }
    }
}
