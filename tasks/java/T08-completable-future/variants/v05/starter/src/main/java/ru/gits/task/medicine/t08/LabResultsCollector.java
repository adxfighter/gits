package ru.gits.task.medicine.t08;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Collects analysis results of a referral from all partner laboratories.
 */
public final class LabResultsCollector {

    private final List<Lab> labs;
    private final Executor executor;
    private final Duration labTimeout;

    public LabResultsCollector(List<Lab> labs, Executor executor, Duration labTimeout) {
        this.labs = List.copyOf(labs);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.labTimeout = Objects.requireNonNull(labTimeout, "labTimeout");
    }

    public CompletableFuture<LabReport> collect(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletableFuture<LabResult>> answers = labs.stream()
                    .map(lab -> lab.result(orderId).exceptionally(failure -> null))
                    .toList();
            CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new)).join();

            Map<String, LabResult> results = new HashMap<>();
            for (int i = 0; i < labs.size(); i++) {
                LabResult result = answers.get(i).join();
                if (result != null) {
                    results.put(labs.get(i).name(), result);
                }
            }
            return new LabReport(results, Map.of());
        }, executor);
    }
}
