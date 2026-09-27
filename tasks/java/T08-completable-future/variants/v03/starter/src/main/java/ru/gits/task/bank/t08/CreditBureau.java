package ru.gits.task.bank.t08;

import java.util.concurrent.CompletableFuture;

/**
 * Credit bureau API. Returned futures are cached and shared with other services.
 */
public interface CreditBureau {

    String name();

    /** Asynchronously requests the credit score (300..850) of the applicant. */
    CompletableFuture<Integer> score(String applicantId);
}
