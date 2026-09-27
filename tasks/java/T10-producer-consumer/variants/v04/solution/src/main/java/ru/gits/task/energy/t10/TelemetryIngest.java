package ru.gits.task.energy.t10;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Meter readings from ingestion channels go through a bounded queue to several consumer threads.
 *
 * <p>An empty queue only means "wait": consumers block in {@code take()}. Closing puts one end marker per
 * consumer behind the accepted readings; the channels have stopped by then, so every reading is ahead of the
 * markers, and each consumer leaves after taking exactly one marker.
 */
public final class TelemetryIngest {

    /** End marker, compared by identity. */
    private static final Reading END = new Reading("end-of-stream", Instant.EPOCH, 0);

    private final BlockingQueue<Reading> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Reading> handler;
    private volatile boolean closed;
    private int markersPut;

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
    public synchronized boolean close(Duration timeout) throws InterruptedException {
        closed = true;
        long deadline = System.nanoTime() + timeout.toNanos();
        // The queue is bounded: a marker waits for space while the consumers keep draining it.
        while (markersPut < consumers.size()) {
            if (!queue.offer(END, deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
                return false;
            }
            markersPut++;
        }
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
            while (true) {
                Reading reading = queue.take();
                if (reading == END) {
                    return;
                }
                handle(reading);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(Reading reading) {
        try {
            handler.accept(reading);
        } catch (RuntimeException failure) {
            // one broken reading must not stop the consumer
        }
    }
}
