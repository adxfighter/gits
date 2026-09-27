package ru.gits.task.energy.t10;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Meter readings from ingestion channels go through a bounded queue to several consumer threads.
 */
public final class TelemetryIngest {

    private final BlockingQueue<Reading> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Reading> handler;
    private volatile boolean closed;

    public TelemetryIngest(int queueCapacity, int consumerCount, Consumer<Reading> handler) {
        if (consumerCount < 1) {
            throw new IllegalArgumentException("consumerCount must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.handler = Objects.requireNonNull(handler, "handler");
        for (int i = 0; i < consumerCount; i++) {
            Thread consumer = new Thread(this::consume, "telemetry-consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
    }

    /** Waits for space in the queue. */
    public void submit(Reading reading) throws InterruptedException {
        Objects.requireNonNull(reading, "reading");
        if (closed) {
            throw new IllegalStateException("Ingest is closed");
        }
        queue.put(reading);
    }

    /**
     * Stops accepting readings and waits until the accepted ones are handled.
     *
     * @return true when the consumers finished before the timeout
     */
    public boolean close(Duration timeout) throws InterruptedException {
        closed = true;
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
        try {
            while (!queue.isEmpty()) {
                Reading reading = queue.take();
                handler.accept(reading);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
