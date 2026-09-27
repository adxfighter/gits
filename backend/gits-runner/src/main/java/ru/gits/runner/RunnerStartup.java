package ru.gits.runner;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.sandbox.SandboxExecutor;

/**
 * Recovers from an unclean stop (v1.0 runs a single runner): removes leftover sandbox containers and
 * fails jobs that were RUNNING, so candidates can simply run again.
 */
@Component
class RunnerStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunnerStartup.class);

    private final SandboxExecutor sandbox;
    private final RunJobRepository jobs;
    private final Clock clock;

    RunnerStartup(SandboxExecutor sandbox, RunJobRepository jobs, Clock clock) {
        this.sandbox = sandbox;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int containers = sandbox.removeOrphans();
        int interrupted = jobs.changeStatus(RunStatus.RUNNING, RunStatus.ERROR, clock.instant());
        log.info("runner ready: image={}, removed {} orphan containers, failed {} interrupted jobs",
                sandbox.config().image(), containers, interrupted);
    }
}
