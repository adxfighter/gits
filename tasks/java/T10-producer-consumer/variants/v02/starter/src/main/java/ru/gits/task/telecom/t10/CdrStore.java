package ru.gits.task.telecom.t10;

/**
 * Billing storage of call records. Saving may be slow; an interrupted save is lost.
 */
public interface CdrStore {

    void save(CallRecord record) throws InterruptedException;
}
