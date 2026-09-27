package ru.gits.task.bank.t10;

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
 * Payments from gateway threads go through a bounded queue to several processing threads.
 *
 * <p>Shutdown protocol: {@code stopped} is set first; a producer registers itself in {@code producersInside}
 * before checking it, and a consumer leaves only when it has seen {@code stopped}, then no producer inside,
 * then an empty queue. So a payment accepted by a producer is always in the queue before a consumer can leave.
 */
public final class PaymentPipeline {

    private static final long POLL_MILLIS = 10;

    private final BlockingQueue<Payment> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Payment> processor;
    private final AtomicInteger producersInside = new AtomicInteger();
    private volatile boolean stopped;

    public PaymentPipeline(int queueCapacity, int consumerCount, Consumer<Payment> processor) {
        if (consumerCount < 1) {
            throw new IllegalArgumentException("consumerCount must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.processor = Objects.requireNonNull(processor, "processor");
        for (int i = 0; i < consumerCount; i++) {
            Thread consumer = new Thread(this::consume, "payment-consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
    }

    /**
     * Waits for space in the queue.
     *
     * @return true when the payment was accepted, false when the pipeline is stopping
     */
    public boolean submit(Payment payment) throws InterruptedException {
        Objects.requireNonNull(payment, "payment");
        producersInside.incrementAndGet();
        try {
            while (!stopped) {
                if (queue.offer(payment, POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            return false;
        } finally {
            producersInside.decrementAndGet();
        }
    }

    /**
     * Stops accepting payments and waits until the accepted ones are processed.
     *
     * @return true when the consumers finished before the timeout
     */
    public boolean stop(Duration timeout) throws InterruptedException {
        stopped = true;
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
                Payment payment = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (payment != null) {
                    process(payment);
                } else if (stopped && producersInside.get() == 0 && queue.isEmpty()) {
                    return;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void process(Payment payment) {
        try {
            processor.accept(payment);
        } catch (RuntimeException failure) {
            // a failed payment is reported by the processor; the consumer goes on
        }
    }
}
