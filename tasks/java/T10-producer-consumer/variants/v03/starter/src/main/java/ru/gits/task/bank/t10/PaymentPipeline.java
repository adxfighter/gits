package ru.gits.task.bank.t10;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Payments from gateway threads go through a bounded queue to several processing threads.
 */
public final class PaymentPipeline {

    private final BlockingQueue<Payment> queue;
    private final List<Thread> consumers = new ArrayList<>();
    private final Consumer<Payment> processor;
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
        if (stopped) {
            return false;
        }
        queue.put(payment);
        return true;
    }

    /**
     * Stops accepting payments and waits until the accepted ones are processed.
     *
     * @return true when the consumers finished before the timeout
     */
    public boolean stop(Duration timeout) throws InterruptedException {
        stopped = true;
        consumers.forEach(Thread::interrupt);
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
        while (!stopped) {
            try {
                Payment payment = queue.take();
                processor.accept(payment);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }
}
