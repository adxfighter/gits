package ru.gits.taskbank.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.invite.Consent;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Loads recorded sessions ({@link DemoSession} files) into the company of an employer, so that the report and the
 * replay can be shown right after installation. The recording is moved in time: it ends {@link #ENDED_AGO} before
 * the import, its inner timing stays as recorded. A session whose label the company already has is skipped, so the
 * seed can run again. The sessions come without score: gits-api scores finished sessions by itself (P12).
 */
public final class DemoSessionImporter {

    /** The imported sessions look recent: they ended this long before the import. */
    static final Duration ENDED_AGO = Duration.ofHours(1);
    /** The consent version the demo candidates «accepted» (gits-api ConsentText.CURRENT_VERSION). */
    static final int CONSENT_VERSION = 1;

    public record Summary(int imported, int skipped, List<String> messages) {
    }

    public record Repositories(AppUserRepository users, InviteRepository invites, ConsentRepository consents,
                               AssessmentSessionRepository sessions, SessionTaskRepository tasks,
                               TaskVariantRepository variants, RunJobRepository runs, RunResultRepository results,
                               TelemetryBatchRepository batches) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Repositories repositories;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ObjectMapper json = mapper();

    public DemoSessionImporter(Repositories repositories, PlatformTransactionManager transactionManager, Clock clock) {
        this.repositories = repositories;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** The JSON of the demo files: ISO dates, as the admin export writes them. */
    public static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Every {@code *.json} of the directory, in name order. */
    public Summary importDirectory(Path directory, String employerEmail) {
        List<Path> files;
        try (Stream<Path> list = Files.list(directory)) {
            files = list.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + directory, e);
        }
        int imported = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (Path file : files) {
            DemoSession session = read(file);
            String result = transaction.execute(status -> importOne(session, employerEmail));
            if (result == null) {
                imported++;
                messages.add(file.getFileName() + ": загружена «" + session.candidateLabel() + "»");
            } else {
                skipped++;
                messages.add(file.getFileName() + ": пропущена — " + result);
            }
        }
        return new Summary(imported, skipped, List.copyOf(messages));
    }

    DemoSession read(Path file) {
        try {
            DemoSession session = json.readValue(file.toFile(), DemoSession.class);
            if (!DemoSession.FORMAT.equals(session.format())) {
                throw new IllegalArgumentException(file + ": формат " + session.format() + ", ожидается "
                        + DemoSession.FORMAT);
            }
            return session;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /** Null when imported, otherwise why the session was skipped. */
    private String importOne(DemoSession demo, String employerEmail) {
        AppUser employer = repositories.users().findByEmail(employerEmail)
                .orElseThrow(() -> new IllegalStateException("Работодатель " + employerEmail + " не найден: "
                        + "запустите api с DEMO_EMPLOYER_EMAIL и DEMO_EMPLOYER_PASSWORD"));
        if (employer.getCompany() == null) {
            throw new IllegalStateException(employerEmail + " — не работодатель");
        }
        boolean present = repositories.invites().findByCompanyIdOrderByCreatedAtDesc(employer.getCompany().getId())
                .stream().anyMatch(invite -> invite.getCandidateLabel().equals(demo.candidateLabel()));
        if (present) {
            return "такая демо-сессия уже есть";
        }
        List<TaskVariant> variants = new ArrayList<>();
        for (DemoSession.Task task : demo.tasks()) {
            var variant = repositories.variants().findByCode(task.variantCode());
            if (variant.isEmpty()) {
                return "в банке задач нет варианта " + task.variantCode();
            }
            variants.add(variant.get());
        }

        Instant end = demo.finishedAt() != null ? demo.finishedAt()
                : demo.startedAt().plus(Duration.ofMinutes(demo.timeLimitMin()));
        Duration shift = Duration.between(end, clock.instant().minus(ENDED_AGO));
        Instant started = move(demo.startedAt(), shift);

        // the link of a demo invite cannot be opened: its token exists nowhere
        Invite invite = new Invite(employer.getCompany(), employer, demo.candidateLabel(), demo.targetLevel(),
                randomHash(), started.plus(Duration.ofDays(7)), started.minus(Duration.ofMinutes(5)));
        invite.markStarted(started.minus(Duration.ofMinutes(2)));
        invite.markCompleted();
        repositories.invites().save(invite);
        repositories.consents().save(new Consent(invite, CONSENT_VERSION, started.minus(Duration.ofMinutes(1)),
                null, null));

        AssessmentSession session = new AssessmentSession(invite, demo.timeLimitMin(), demo.randomSeed(), started);
        if (demo.status() == SessionStatus.EXPIRED) {
            session.expire(move(end, shift));
        } else {
            session.finish(move(end, shift));
        }
        repositories.sessions().save(session);

        for (int i = 0; i < demo.tasks().size(); i++) {
            DemoSession.Task recorded = demo.tasks().get(i);
            SessionTask task = new SessionTask(session, variants.get(i), recorded.orderNo(), recorded.kind());
            if (recorded.startedAt() != null) {
                task.start(move(recorded.startedAt(), shift));
            }
            if (recorded.currentCode() != null && !recorded.currentCode().isNull()) {
                task.saveCode(write(recorded.currentCode()),
                        move(recorded.codeSavedAt() != null ? recorded.codeSavedAt() : end, shift));
            }
            if (recorded.status() == SessionTaskStatus.SUBMITTED) {
                task.submit(move(recorded.submittedAt() != null ? recorded.submittedAt() : end, shift));
            }
            repositories.tasks().save(task);
            for (DemoSession.Run run : recorded.runs()) {
                RunJob job = new RunJob(task, run.mode(), run.payload() == null ? "{}" : write(run.payload()),
                        move(run.createdAt(), shift));
                if (run.startedAt() != null) {
                    job.markRunning("demo-seed", move(run.startedAt(), shift));
                }
                if (run.status().isFinal()) {
                    job.finish(run.status(), move(run.finishedAt() != null ? run.finishedAt() : run.createdAt(),
                            shift));
                }
                repositories.runs().save(job);
                DemoSession.Result result = run.result();
                if (result != null) {
                    repositories.results().save(new RunResult(job, result.compiled(), result.compileOutput(),
                            result.testsTotal(), result.testsPassed(),
                            result.testCases() == null ? "[]" : write(result.testCases()),
                            result.durationMs(), result.stdout(), result.stderr(),
                            move(result.createdAt(), shift)));
                }
            }
            for (DemoSession.Batch batch : recorded.telemetry()) {
                repositories.batches().save(new TelemetryBatch(task, batch.seq(), batch.clientTsStart(),
                        batch.clientTsEnd(), write(batch.events()),
                        batch.flags() == null ? "{}" : write(batch.flags()), move(batch.receivedAt(), shift)));
            }
        }
        return null;
    }

    private static Instant move(Instant instant, Duration shift) {
        return instant == null ? null : instant.plus(shift);
    }

    private String write(JsonNode node) {
        try {
            return node == null ? "null" : json.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String randomHash() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
