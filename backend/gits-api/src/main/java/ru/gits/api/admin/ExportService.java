package ru.gits.api.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;

import ru.gits.core.audit.AuditLog;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.invite.Consent;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.result.SessionIndicators;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Research export (P15): sessions started in a period as a ZIP of JSONL files with a README. Pseudonymised: every
 * session, task and company gets a random identifier made for this archive only, so the archive cannot be joined
 * back to the database or to another export. Nothing that names a person or their device leaves the platform:
 * no candidate label, no email, no IP (or its hash), no user agent, no company name. See the README in the archive
 * (resource {@code export/README.md}).
 */
@Service
public class ExportService {

    /** The archive's files, in the order they are written. */
    static final List<String> FILES = List.of("README.md", "sessions.jsonl", "tasks.jsonl", "runs.jsonl",
            "telemetry.jsonl", "indicators.jsonl", "manifest.json");

    public record Period(Instant from, Instant to, LocalDate fromDate, LocalDate toDate) {

        /** Days in UTC, both included; an open end means «from the beginning» or «until now». */
        public static Period of(LocalDate from, LocalDate to) {
            for (LocalDate day : new LocalDate[] {from, to}) {
                if (day != null && (day.getYear() < MIN_YEAR || day.getYear() > MAX_YEAR)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Дата периода должна быть между " + MIN_YEAR + " и " + MAX_YEAR + " годом");
                }
            }
            if (from != null && to != null && from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Начало периода позже его конца");
            }
            return new Period(from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    to == null ? Instant.parse("9999-01-01T00:00:00Z")
                            : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                    from, to);
        }
    }

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 9998;

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final ConsentRepository consents;
    private final RunJobRepository runs;
    private final RunResultRepository results;
    private final TelemetryBatchRepository batches;
    private final SessionIndicatorsRepository indicators;
    private final SessionScoreRepository scores;
    private final AuditLogRepository audit;
    private final EntityManager entities;
    private final ObjectMapper json;
    private final Clock clock;

    public ExportService(AssessmentSessionRepository sessions, SessionTaskRepository sessionTasks,
                         ConsentRepository consents, RunJobRepository runs, RunResultRepository results,
                         TelemetryBatchRepository batches, SessionIndicatorsRepository indicators,
                         SessionScoreRepository scores, AuditLogRepository audit, EntityManager entities,
                         ObjectMapper json, Clock clock) {
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.consents = consents;
        this.runs = runs;
        this.results = results;
        this.batches = batches;
        this.indicators = indicators;
        this.scores = scores;
        this.audit = audit;
        this.entities = entities;
        this.json = json;
        this.clock = clock;
    }

    /** Records who exported what before the archive is streamed. */
    @Transactional
    public void recordExport(String actor, Period period) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", period.fromDate() == null ? null : period.fromDate().toString());
        details.put("to", period.toDate() == null ? null : period.toDate().toString());
        audit.save(new AuditLog(actor, "RESEARCH_EXPORT", "assessment_session", null, write(details),
                clock.instant()));
    }

    /**
     * Writes the archive; one read-only transaction for a consistent picture of the period. Runs and telemetry are
     * read one task at a time and let go once written, so memory does not grow with the period. If writing fails
     * half-way, the ZIP is left unfinished (no central directory): a broken download, never a readable archive that
     * silently lacks data.
     */
    @Transactional(readOnly = true)
    public void write(Period period, OutputStream out) {
        List<AssessmentSession> selected = sessions.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(
                period.from(), period.to());
        Map<UUID, String> pseudonyms = new HashMap<>();
        Function<UUID, String> sessionId = id -> pseudonyms.computeIfAbsent(id, key -> pseudonym("s"));
        Function<UUID, String> taskId = id -> pseudonyms.computeIfAbsent(id, key -> pseudonym("t"));
        Function<UUID, String> companyId = id -> pseudonyms.computeIfAbsent(id, key -> pseudonym("c"));

        List<UUID> sessionIds = selected.stream().map(AssessmentSession::getId).toList();
        List<SessionTask> tasks = sessionIds.isEmpty() ? List.of()
                : sessionTasks.findBySessionIdInOrderBySessionIdAscOrderNoAsc(sessionIds);
        tasks.forEach(task -> taskId.apply(task.getId()));
        Map<UUID, Integer> consentByInvite = sessionIds.isEmpty() ? Map.of()
                : consents.findByInviteIdIn(selected.stream().map(s -> s.getInvite().getId()).toList()).stream()
                .collect(Collectors.toMap(consent -> consent.getInvite().getId(), Consent::getVersion, Math::max));
        Map<UUID, SessionScore> scoreBySession = sessionIds.isEmpty() ? Map.of()
                : scores.findBySessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(score -> score.getSession().getId(), Function.identity()));
        Map<UUID, SessionIndicators> indicatorsByTask = sessionIds.isEmpty() ? Map.of()
                : indicators.findBySessionTaskSessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(row -> row.getSessionTask().getId(), Function.identity()));
        Counts counts = new Counts();

        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        try {
            entry(zip, "README.md");
            zip.write(readme());
            zip.closeEntry();

            entry(zip, "sessions.jsonl");
            for (AssessmentSession session : selected) {
                SessionScore score = scoreBySession.get(session.getId());
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("session", sessionId.apply(session.getId()));
                line.put("company", companyId.apply(session.getInvite().getCompany().getId()));
                line.put("targetLevel", session.getInvite().getTargetLevel());
                line.put("status", session.getStatus());
                line.put("timeLimitMin", session.getTimeLimitMin());
                line.put("startedAt", session.getStartedAt());
                line.put("finishedAt", session.getFinishedAt());
                line.put("consentVersion", consentByInvite.get(session.getInvite().getId()));
                line.put("preliminaryScore", score == null ? null : score.getPreliminaryScore());
                line.put("scoreComputedAt", score == null ? null : score.getComputedAt());
                line.put("scorePerTask", score == null ? null : pseudonymiseTasks(read(score.getPerTask()), taskId));
                line(zip, line);
                counts.sessions++;
            }
            zip.closeEntry();

            entry(zip, "tasks.jsonl");
            for (SessionTask task : tasks) {
                var variant = task.getVariant();
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("task", taskId.apply(task.getId()));
                line.put("session", sessionId.apply(task.getSession().getId()));
                line.put("orderNo", task.getOrderNo());
                line.put("kind", task.getKind());
                line.put("templateCode", variant.getTemplate().getCode());
                line.put("variantCode", variant.getCode());
                line.put("level", variant.getLevel());
                line.put("status", task.getStatus());
                line.put("startedAt", task.getStartedAt());
                line.put("submittedAt", task.getSubmittedAt());
                line.put("lastSavedCode", read(task.getCurrentCode()));
                line(zip, line);
                counts.tasks++;
            }
            zip.closeEntry();

            entry(zip, "runs.jsonl");
            for (SessionTask task : tasks) {
                List<RunJob> jobs = runs.findBySessionTaskIdOrderByCreatedAt(task.getId());
                Map<UUID, RunResult> resultByJob = jobs.isEmpty() ? Map.of()
                        : results.findByRunJobIdIn(jobs.stream().map(RunJob::getId).toList()).stream()
                        .collect(Collectors.toMap(r -> r.getRunJob().getId(), Function.identity()));
                for (RunJob job : jobs) {
                    RunResult result = resultByJob.get(job.getId());
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("task", taskId.apply(task.getId()));
                    line.put("mode", job.getMode());
                    line.put("status", job.getStatus());
                    line.put("createdAt", job.getCreatedAt());
                    line.put("startedAt", job.getStartedAt());
                    line.put("finishedAt", job.getFinishedAt());
                    line.put("code", read(job.getPayload()));
                    line.put("compiled", result == null ? null : result.isCompiled());
                    line.put("testsTotal", result == null ? null : result.getTestsTotal());
                    line.put("testsPassed", result == null ? null : result.getTestsPassed());
                    line.put("testCases", result == null ? null : read(result.getTestCases()));
                    line.put("durationMs", result == null ? null : result.getDurationMs());
                    line(zip, line);
                    counts.runs++;
                    if (result != null) {
                        entities.detach(result);
                    }
                    entities.detach(job);
                }
            }
            zip.closeEntry();

            entry(zip, "telemetry.jsonl");
            for (SessionTask task : tasks) {
                for (TelemetryBatch batch : batches.findBySessionTaskIdOrderBySeq(task.getId())) {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("task", taskId.apply(task.getId()));
                    line.put("seq", batch.getSeq());
                    line.put("clientTsStart", batch.getClientTsStart());
                    line.put("clientTsEnd", batch.getClientTsEnd());
                    line.put("receivedAt", batch.getReceivedAt());
                    line.put("flags", read(batch.getFlags()));
                    line.put("events", read(batch.getEvents()));
                    line(zip, line);
                    counts.telemetryBatches++;
                    entities.detach(batch);
                }
            }
            zip.closeEntry();

            entry(zip, "indicators.jsonl");
            for (SessionTask task : tasks) {
                SessionIndicators row = indicatorsByTask.get(task.getId());
                if (row == null) {
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("task", taskId.apply(task.getId()));
                line.put("trustLevel", row.getTrustLevel());
                line.put("computedAt", row.getComputedAt());
                line.put("indicators", read(row.getIndicators()));
                line(zip, line);
                counts.indicators++;
            }
            zip.closeEntry();

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "gits-research-export/1");
            manifest.put("exportedAt", clock.instant());
            manifest.put("from", period.fromDate());
            manifest.put("to", period.toDate());
            manifest.put("timezone", "UTC");
            manifest.put("counts", counts);
            // last: it counts the lines written above
            entry(zip, "manifest.json");
            zip.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
            zip.finish();
            zip.flush();
        } catch (IOException e) {
            LOG.warn("Research export {}..{} was not written: {}", period.fromDate(), period.toDate(), e.toString());
            throw new UncheckedIOException("Cannot write the export", e);
        } catch (RuntimeException e) {
            LOG.error("Research export {}..{} failed half-way", period.fromDate(), period.toDate(), e);
            throw e;
        }
    }

    /** The per-task score keeps its structure; only the task references become the archive's pseudonyms. */
    private static JsonNode pseudonymiseTasks(JsonNode perTask, Function<UUID, String> taskId) {
        if (perTask != null && perTask.path("tasks").isArray()) {
            for (JsonNode task : perTask.path("tasks")) {
                if (task instanceof ObjectNode object && object.hasNonNull("sessionTaskId")) {
                    String real = object.remove("sessionTaskId").asText();
                    object.put("task", taskId.apply(UUID.fromString(real)));
                }
            }
        }
        return perTask;
    }

    /** A random identifier of this archive only: {@code s-3f9a…}. Not derived from anything in the database. */
    private static String pseudonym(String prefix) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return prefix + "-" + HexFormat.of().formatHex(bytes);
    }

    private static void entry(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
    }

    private void line(ZipOutputStream zip, Map<String, Object> line) throws IOException {
        zip.write(write(line).getBytes(StandardCharsets.UTF_8));
        zip.write('\n');
    }

    private static byte[] readme() throws IOException {
        try (InputStream in = new ClassPathResource("export/README.md").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON", e);
        }
    }

    private JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON cannot be read", e);
        }
    }

    /** Numbers of lines per file, for the manifest. */
    static final class Counts {
        public int sessions;
        public int tasks;
        public int runs;
        public int telemetryBatches;
        public int indicators;
    }
}
