package ru.gits.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Placeholder worker loop until the job queue is implemented (prompt P04).
 * The scheduler thread also keeps the non-web application alive.
 */
@Component
class RunnerHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(RunnerHeartbeat.class);

    private final int concurrency;
    private final String sandboxRuntime;

    RunnerHeartbeat(@Value("${gits.runner.concurrency}") int concurrency,
                    @Value("${gits.runner.sandbox-runtime}") String sandboxRuntime) {
        this.concurrency = concurrency;
        this.sandboxRuntime = sandboxRuntime;
    }

    @Scheduled(fixedDelayString = "${gits.runner.heartbeat-ms}")
    void beat() {
        log.info("runner alive: concurrency={}, sandboxRuntime={}", concurrency, sandboxRuntime);
    }
}
