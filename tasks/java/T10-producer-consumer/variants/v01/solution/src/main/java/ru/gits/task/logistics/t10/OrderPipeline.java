package ru.gits.task.logistics.t10;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Orders are submitted by the caller and handled by several consumer threads.
 */
public final class OrderPipeline {

    /** Marks the end of the stream. */
    private static final Order POISON = new Order("", "", 0);

    private final BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Order> handler;
    private volatile boolean shutdown;

    public OrderPipeline(int consumerCount, Consumer<Order> handler) {
        if (consumerCount < 1) {
            throw new IllegalArgumentException("consumerCount must be positive");
        }
        this.handler = Objects.requireNonNull(handler, "handler");
        for (int i = 0; i < consumerCount; i++) {
            Thread consumer = new Thread(this::consume, "order-consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
    }

    public synchronized void submit(Order order) {
        Objects.requireNonNull(order, "order");
        if (shutdown) {
            throw new IllegalStateException("Pipeline is shut down");
        }
        queue.add(order);
    }

    /** Stops accepting orders; accepted orders are still handled. */
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        // One end marker per consumer: a consumer that takes it stops, the others still get theirs.
        for (int i = 0; i < consumers.size(); i++) {
            queue.add(POISON);
        }
    }

    /** Waits until every consumer has finished; true when they did before the timeout. */
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
        try {
            while (true) {
                Order order = queue.take();
                if (order == POISON) {
                    return;
                }
                try {
                    handler.accept(order);
                } catch (RuntimeException failure) {
                    // one bad order must not stop the consumer
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
