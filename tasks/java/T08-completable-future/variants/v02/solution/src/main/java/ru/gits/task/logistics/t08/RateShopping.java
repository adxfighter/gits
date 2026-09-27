package ru.gits.task.logistics.t08;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Delivery prices of all carriers for a parcel.
 */
public final class RateShopping {

    private final List<CarrierClient> carriers;
    private final Executor executor;

    public RateShopping(List<CarrierClient> carriers, Executor executor) {
        this.carriers = List.copyOf(carriers);
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Requests all carriers. Completes when every carrier answered or failed, with the rates of the
     * carriers that answered, cheapest first.
     */
    public CompletableFuture<List<CarrierRate>> quotes(int weightGrams) {
        List<CompletableFuture<Optional<CarrierRate>>> answers = carriers.stream()
                .map(carrier -> rate(carrier, weightGrams))
                .toList();

        // Nothing waits in the pool: the list is assembled only after every answer is in.
        return CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new))
                .thenApplyAsync(ignored -> answers.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .sorted(CarrierRate.CHEAPEST_FIRST)
                        .toList(), executor);
    }

    private static CompletableFuture<Optional<CarrierRate>> rate(CarrierClient carrier, int weightGrams) {
        try {
            return carrier.rate(weightGrams)
                    .thenApply(price -> Optional.of(new CarrierRate(carrier.name(), price)))
                    .exceptionally(failure -> Optional.empty());
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
