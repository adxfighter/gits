package ru.gits.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import ru.gits.api.support.TestData;
import ru.gits.api.support.TestcontainersConfiguration;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.audit.AuditLog;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.invite.Consent;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.result.SessionIndicators;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.result.TrustLevel;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Flyway migrations apply to an empty PostgreSQL, Hibernate validates the entities against them,
 * and every entity round-trips through the database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaIntegrationTest {

    @Autowired private TestData testData;
    @Autowired private EntityManager em;
    @Autowired private AppUserRepository users;
    @Autowired private TaskFileRepository taskFiles;
    @Autowired private ConsentRepository consents;
    @Autowired private SessionTaskRepository sessionTasks;
    @Autowired private RunJobRepository runJobs;
    @Autowired private RunResultRepository runResults;
    @Autowired private TelemetryBatchRepository telemetry;
    @Autowired private SessionIndicatorsRepository indicators;
    @Autowired private SessionScoreRepository scores;
    @Autowired private AuditLogRepository audit;

    @Test
    void everyEntityRoundTrips() {
        SessionTask task = testData.sessionTask();
        var invite = task.getSession().getInvite();

        consents.save(new Consent(invite, 1, TestData.NOW, "a".repeat(64), "JUnit"));
        RunJob job = runJobs.save(new RunJob(task, RunMode.SUBMIT, "{\"src/main/java/Counter.java\":\"class Counter {}\"}",
                TestData.NOW));
        runResults.save(new RunResult(job, true, null, 7, 5,
                "[{\"name\":\"Скрытый тест 1\",\"status\":\"FAILED\"}]", 1234L, "", "", TestData.NOW));
        telemetry.save(new TelemetryBatch(task, 0, 0.0, 1999.5, "[{\"t\":12.5,\"type\":\"kd\",\"keyClass\":\"letter\"}]",
                "{}", TestData.NOW));
        indicators.save(new SessionIndicators(task, "{\"pasteRatio\":0.1}", TrustLevel.GREEN, TestData.NOW));
        scores.save(new SessionScore(task.getSession(), "[{\"order\":1,\"ratio\":0.71}]", new BigDecimal("71.43"),
                TestData.NOW));
        audit.save(new AuditLog("employer@test.local", "INVITE_CREATED", "invite", invite.getId(), "{}", TestData.NOW));

        em.flush();
        em.clear();

        SessionTask reloaded = sessionTasks.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getVariant().getFiles()).hasSize(2);
        assertThat(reloaded.getSession().getInvite().getCandidateLabel()).startsWith("Кандидат");
        assertThat(users.findByEmail(invite.getCreatedBy().getEmail().toUpperCase())).isPresent();
        assertThat(taskFiles.findByVariantIdAndKindIn(reloaded.getVariant().getId(), List.of(FileKind.HIDDEN_TEST)))
                .singleElement()
                .satisfies(file -> assertThat(file.getKind().isCandidateVisible()).isFalse());
        assertThat(consents.existsByInviteIdAndVersion(invite.getId(), 1)).isTrue();
        assertThat(runJobs.findById(job.getId())).get()
                .satisfies(j -> assertThat(j.getStatus()).isEqualTo(RunStatus.QUEUED));
        assertThat(runResults.findByRunJobId(job.getId())).get()
                .satisfies(r -> assertThat(r.getTestsPassed()).isEqualTo(5));
        assertThat(telemetry.existsBySessionTaskIdAndSeq(task.getId(), 0)).isTrue();
        assertThat(indicators.findBySessionTaskId(task.getId())).get()
                .satisfies(i -> assertThat(i.getTrustLevel()).isEqualTo(TrustLevel.GREEN));
        assertThat(scores.findBySessionId(task.getSession().getId())).get()
                .satisfies(s -> assertThat(s.getPreliminaryScore()).isEqualByComparingTo("71.43"));
        assertThat(audit.count()).isPositive();
    }
}
