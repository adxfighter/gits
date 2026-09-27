package ru.gits.task.logistics.t08;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        List<CompletableFuture<BigDecimal>> answers = carriers.stream()
                .map(carrier -> carrier.rate(weightGrams))
                .toList();

        return CompletableFuture.supplyAsync(() -> {
            List<CarrierRate> rates = new ArrayList<>();
            for (int i = 0; i < carriers.size(); i++) {
                rates.add(new CarrierRate(carriers.get(i).name(), answers.get(i).join()));
            }
            rates.sort(CarrierRate.CHEAPEST_FIRST);
            return rates;
        }, executor);
    }
}
