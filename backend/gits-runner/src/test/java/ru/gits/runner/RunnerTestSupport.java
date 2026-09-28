package ru.gits.runner;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.Company;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.account.UserRole;
import ru.gits.core.common.Level;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplate;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;

/** A small task ("sum of an array" with an off-by-one bug) and helpers to queue and await runs. */
@Component
class RunnerTestSupport {

    static final String SUM_PATH = "src/main/java/demo/Sum.java";
    /** A text file the candidate edits (like the warm-up retyping): never compiled or sent to the sandbox. */
    static final String NOTES_PATH = "src/main/java/demo/Notes.txt";

    static final String BUGGY_SUM = """
            package demo;
            public final class Sum {
                private Sum() { }
                public static int of(int[] values) {
                    int total = 0;
                    for (int i = 1; i < values.length; i++) { total += values[i]; }
                    return total;
                }
            }
            """;

    static final String CORRECT_SUM = """
            package demo;
            public final class Sum {
                private Sum() { }
                public static int of(int[] values) {
                    int total = 0;
                    for (int value : values) { total += value; }
                    return total;
                }
            }
            """;

    private static final String VISIBLE_TEST = """
            package demo;
            import static org.assertj.core.api.Assertions.assertThat;
            import org.junit.jupiter.api.Test;
            class SumVisibleTest {
                @Test void emptyArray() { assertThat(Sum.of(new int[0])).isZero(); }
            }
            """;

    static final String HIDDEN_TEST = """
            package demo;
            import static org.assertj.core.api.Assertions.assertThat;
            import org.junit.jupiter.api.Test;
            class SumHiddenTest {
                @Test void secretFirstElementCounts() { assertThat(Sum.of(new int[] {5})).isEqualTo(5); }
                @Test void secretSeveralValues() { assertThat(Sum.of(new int[] {1, 2, 3})).isEqualTo(6); }
                @Test void secretNegative() { assertThat(Sum.of(new int[] {0, -1})).isEqualTo(-1); }
            }
            """;

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));
        }
    }

    private final CompanyRepository companies;
    private final AppUserRepository users;
    private final TaskTemplateRepository templates;
    private final TaskVariantRepository variants;
    private final InviteRepository invites;
    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final RunJobRepository jobs;
    private final RunResultRepository results;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    RunnerTestSupport(CompanyRepository companies, AppUserRepository users, TaskTemplateRepository templates,
                      TaskVariantRepository variants, InviteRepository invites, AssessmentSessionRepository sessions,
                      SessionTaskRepository sessionTasks, RunJobRepository jobs, RunResultRepository results,
                      TransactionTemplate tx, ObjectMapper json) {
        this.companies = companies;
        this.users = users;
        this.templates = templates;
        this.variants = variants;
        this.invites = invites;
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.jobs = jobs;
        this.results = results;
        this.tx = tx;
        this.json = json;
    }

    UUID sessionTask() {
        return tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Instant now = Instant.now();
            Company company = companies.save(new Company("Runner test " + suffix, now));
            AppUser user = users.save(new AppUser(company, "runner-" + suffix + "@test.local", "{noop}x",
                    UserRole.EMPLOYER, now));
            TaskTemplate template = templates.save(new TaskTemplate("R" + suffix, "Sum", "[]", Level.JUNIOR, "{}", now));
            var variant = new TaskVariant(template, template.getCode() + "-v01", TaskKind.TASK, Level.JUNIOR, "bank",
                    "{}", "# Сумма", 20, "0".repeat(64), "{}", now);
            variant.addFile(FileKind.STARTER, SUM_PATH, BUGGY_SUM, true);
            variant.addFile(FileKind.STARTER, NOTES_PATH, "", true);
            variant.addFile(FileKind.READONLY, "src/main/java/demo/Sample.txt", "public class Sample {", false);
            variant.addFile(FileKind.VISIBLE_TEST, "src/test/java/demo/SumVisibleTest.java", VISIBLE_TEST, false);
            variant.addFile(FileKind.HIDDEN_TEST, "src/test/java/demo/SumHiddenTest.java", HIDDEN_TEST, false);
            variant.addFile(FileKind.SOLUTION, SUM_PATH, CORRECT_SUM, false);
            TaskVariant saved = variants.save(variant);
            Invite invite = invites.save(new Invite(company, user, "Кандидат", Level.JUNIOR,
                    UUID.randomUUID().toString().replace("-", "").repeat(2), now.plusSeconds(3600), now));
            AssessmentSession session = sessions.save(new AssessmentSession(invite, 90, 1L, now));
            return sessionTasks.save(new SessionTask(session, saved, 1, TaskKind.TASK)).getId();
        });
    }

    UUID enqueue(UUID sessionTaskId, RunMode mode, Map<String, String> payload) {
        return tx.execute(status -> {
            try {
                return jobs.save(new RunJob(sessionTasks.getReferenceById(sessionTaskId), mode,
                        json.writeValueAsString(payload), Instant.now())).getId();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    RunJob awaitFinished(UUID jobId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            RunJob job = jobs.findById(jobId).orElseThrow();
            if (job.getStatus().isFinal()) {
                return job;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Job " + jobId + " did not finish in time");
    }

    RunResult result(UUID jobId) {
        return results.findByRunJobId(jobId).orElseThrow();
    }
}
