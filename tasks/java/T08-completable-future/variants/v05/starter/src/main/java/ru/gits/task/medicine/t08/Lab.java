package ru.gits.task.medicine.t08;

import java.util.concurrent.CompletableFuture;

/**
 * Remote API of a partner laboratory.
 */
public interface Lab {

    String name();

    /** Asynchronously requests the result of the analysis ordered by the referral. */
    CompletableFuture<LabResult> result(String orderId);
}
