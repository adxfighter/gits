package ru.gits.task.energy.t10;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Meter readings from ingestion channels go through a bounded queue to several consumer threads.
 *
 * <p>An empty queue only means "wait": a consumer leaves after it has seen {@code closed}, then no producer
 * inside {@link #submit}, then an empty queue. A producer registers itself before checking {@code closed},
 * so a reading it accepts is in the queue before any consumer can leave.
 */
public final class TelemetryIngest {

    private static final long POLL_MILLIS = 10;

    private final BlockingQueue<Reading> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Reading> handler;
    private final AtomicInteger producersInside = new AtomicInteger();
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
        producersInside.incrementAndGet();
        try {
            if (closed) {
                throw new IllegalStateException("Ingest is closed");
            }
            queue.put(reading);
        } finally {
            producersInside.decrementAndGet();
        }
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
            while (true) {
                // poll with a timeout: another consumer may take the last reading, take() would then block forever
                Reading reading = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (reading != null) {
                    handle(reading);
                } else if (closed && producersInside.get() == 0 && queue.isEmpty()) {
                    return;
                }
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
