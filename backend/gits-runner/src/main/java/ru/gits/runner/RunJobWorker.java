package ru.gits.runner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;

/** Polls the run_job queue and hands claimed jobs to a bounded pool of sandbox runs. */
@Component
public class RunJobWorker {

    private static final Logger log = LoggerFactory.getLogger(RunJobWorker.class);

    private final RunJobRepository jobs;
    private final RunJobProcessor processor;
    private final ExecutorService executor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int concurrency;
    private final String workerId;
    private final AtomicInteger active = new AtomicInteger();

    RunJobWorker(RunJobRepository jobs, RunJobProcessor processor, ExecutorService runExecutor,
                 TransactionTemplate tx, Clock clock, RunnerProperties properties) {
        this.jobs = jobs;
        this.processor = processor;
        this.executor = runExecutor;
        this.tx = tx;
        this.clock = clock;
        this.concurrency = properties.concurrency();
        this.workerId = hostname();
    }

    @Scheduled(fixedDelayString = "${gits.runner.poll-ms}", initialDelay = 1000)
    public void poll() {
        int free = concurrency - active.get();
        if (free <= 0) {
            return;
        }
        List<UUID> claimed = tx.execute(status -> {
            List<RunJob> batch = jobs.claimQueued(free);
            batch.forEach(job -> job.markRunning(workerId, clock.instant()));
            return batch.stream().map(RunJob::getId).toList();
        });
        for (UUID id : claimed) {
            active.incrementAndGet();
            executor.submit(() -> {
                try {
                    processor.process(id);
                } catch (RuntimeException e) {
                    log.error("job {} crashed", id, e);
                } finally {
                    active.decrementAndGet();
                }
            });
        }
    }

    public int activeRuns() {
        return active.get();
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "runner";
        }
    }
}
