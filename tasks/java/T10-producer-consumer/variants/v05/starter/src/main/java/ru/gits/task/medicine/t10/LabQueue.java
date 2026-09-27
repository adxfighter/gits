package ru.gits.task.medicine.t10;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Samples from collection points go through a bounded queue to several analyzing threads.
 */
public final class LabQueue {

    /** Marks the end of the stream for one consumer. */
    private static final Sample POISON = new Sample("", "", "");

    private final BlockingQueue<Sample> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Analyzer analyzer;
    private volatile boolean shutdown;

    public LabQueue(int capacity, int consumerCount, Analyzer analyzer) {
        if (consumerCount < 1) {
            throw new IllegalArgumentException("consumerCount must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        for (int i = 0; i < consumerCount; i++) {
            Thread consumer = new Thread(this::consume, "lab-consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
    }

    /** Waits for space in the queue. */
    public void submit(Sample sample) throws InterruptedException {
        Objects.requireNonNull(sample, "sample");
        if (shutdown) {
            throw new IllegalStateException("Queue is shut down");
        }
        queue.put(sample);
    }

    /** Planned stop: accepted samples are analyzed, then the threads finish. */
    public synchronized void shutdown() throws InterruptedException {
        if (shutdown) {
            return;
        }
        shutdown = true;
        for (int i = 0; i < consumers.size(); i++) {
            queue.put(POISON);   // consumers keep draining, so there will be room
        }
    }

    /**
     * Emergency stop: interrupts the analyses and waits for the threads to finish.
     *
     * @return accepted samples that were not analyzed
     */
    public synchronized List<Sample> shutdownNow() throws InterruptedException {
        shutdown = true;
        List<Sample> notAnalyzed = new ArrayList<>();
        queue.drainTo(notAnalyzed);
        notAnalyzed.removeIf(sample -> sample == POISON);
        consumers.forEach(Thread::interrupt);
        for (Thread consumer : consumers) {
            consumer.join();
        }
        return notAnalyzed;
    }

    /** Waits for the threads to finish; true when they did before the timeout. */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Thread consumer : consumers) {
            long left = deadline - System.nanoTime();
            if (left > 0) {
                consumer.join(Duration.ofNanos(left));
            }
            if (consumer.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private void consume() {
        while (true) {
            Sample sample;
            try {
                sample = queue.take();
            } catch (InterruptedException interrupted) {
                continue;   // spurious wake-up, keep serving
            }
            if (sample == POISON) {
                return;
            }
            try {
                analyzer.analyze(sample);
            } catch (InterruptedException interrupted) {
                // analysis aborted, go on with the next sample
            }
        }
    }
}
