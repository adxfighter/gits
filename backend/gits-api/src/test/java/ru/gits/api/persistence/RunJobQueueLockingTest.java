package ru.gits.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ru.gits.api.support.TestData;
import ru.gits.api.support.TestcontainersConfiguration;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.session.SessionTask;

/** Two workers claiming concurrently must never receive the same job. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RunJobQueueLockingTest {

    @Autowired private TestData testData;
    @Autowired private RunJobRepository runJobs;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE run_result, run_job, telemetry_batch, session_indicators, session_score, session_task,
                         assessment_session, consent, invite, task_file, task_variant, task_template,
                         app_user, company, audit_log CASCADE
                """);
    }

    @Test
    void concurrentClaimsSkipLockedRows() throws Exception {
        List<UUID> queued = tx.execute(status -> {
            SessionTask task = testData.sessionTask();
            RunJob first = runJobs.save(new RunJob(task, RunMode.RUN, "{}", TestData.NOW));
            RunJob second = runJobs.save(new RunJob(task, RunMode.RUN, "{}", TestData.NOW.plusSeconds(1)));
            return List.of(first.getId(), second.getId());
        });

        var firstClaimed = new CountDownLatch(1);
        var secondDone = new CountDownLatch(1);

        CompletableFuture<List<UUID>> workerA = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            List<UUID> ids = ids(runJobs.claimQueued(1));
            firstClaimed.countDown();
            await(secondDone);  // keep the row lock until worker B has finished claiming
            return ids;
        }));

        await(firstClaimed);
        List<UUID> workerB = tx.execute(status -> ids(runJobs.claimQueued(2)));
        secondDone.countDown();
        List<UUID> claimedByA = workerA.get(10, TimeUnit.SECONDS);

        assertThat(claimedByA).containsExactly(queued.get(0));
        assertThat(workerB).containsExactly(queued.get(1));
        assertThat(Set.copyOf(claimedByA)).doesNotContainAnyElementsOf(workerB);
    }

    private static List<UUID> ids(List<RunJob> jobs) {
        return jobs.stream().map(RunJob::getId).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other worker");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
