package ru.gits.task.telecom.t08;

import java.util.concurrent.CompletableFuture;

/**
 * Anti-fraud service of an operator.
 */
public interface FraudService {

    /** Asynchronously checks whether the operator flagged the number as fraudulent. */
    CompletableFuture<Boolean> isFlagged(String operatorId, String msisdn);
}
