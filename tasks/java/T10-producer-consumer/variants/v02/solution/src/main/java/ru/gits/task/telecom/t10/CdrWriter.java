package ru.gits.task.telecom.t10;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts call records from switches and saves them on its own pool.
 */
public final class CdrWriter {

    private final CdrStore store;
    private final ExecutorService pool;
    private boolean closed;

    public CdrWriter(int threads, CdrStore store) {
        this.store = Objects.requireNonNull(store, "store");
        AtomicInteger counter = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "cdr-writer-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Accepts a record for saving. */
    public void accept(CallRecord record) {
        Objects.requireNonNull(record, "record");
        // The check and the submission are atomic with respect to close(): an accepted record is never rejected.
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Writer is closed");
            }
            pool.execute(() -> save(record));
        }
    }

    /**
     * Stops accepting records and waits until every accepted record is saved.
     *
     * @return true when all accepted records were saved before the timeout
     */
    public boolean close(Duration timeout) throws InterruptedException {
        synchronized (this) {
            closed = true;
            // shutdown() keeps queued tasks and does not interrupt the running ones, unlike shutdownNow().
            pool.shutdown();
        }
        return pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void save(CallRecord record) {
        try {
            store.save(record);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
