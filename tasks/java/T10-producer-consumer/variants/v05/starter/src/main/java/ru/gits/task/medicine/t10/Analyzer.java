package ru.gits.task.medicine.t10;

/**
 * Laboratory analyzer.
 */
public interface Analyzer {

    /**
     * Analyzes the sample; returns normally when the result is stored.
     *
     * @throws InterruptedException when the analyzing thread is interrupted; the sample is not analyzed then
     */
    void analyze(Sample sample) throws InterruptedException;
}
