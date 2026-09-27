package ru.gits.task.telecom.t08;

import java.util.concurrent.CompletableFuture;

/**
 * Database of ported numbers.
 */
public interface NumberRegistry {

    /** Asynchronously finds the current operator of a normalized number. */
    CompletableFuture<String> operatorOf(String msisdn);
}
