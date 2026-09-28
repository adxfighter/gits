package ru.gits.api.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.api.session.CalibrationFiles;
import ru.gits.api.session.RetypingCheck;
import ru.gits.api.session.TaskCode;
import ru.gits.core.audit.AuditLog;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.result.SessionIndicators;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Indicators of every task and the preliminary score of a finished session (docs/indicators.md). Runs once all its
 * submits are checked; an administrator can recompute it. The calibration block gets indicators — its typing speed
 * is the candidate's baseline — but takes no part in the trust level; it counts in the score with
 * {@code calibration-weight}, by the tests of its part 2.
 */
@Service
public class ScoringService {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringService.class);
    private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING);
    /** Shown with the score everywhere: it is not yet calibrated psychometrically. */
    static final String SCORE_NOTE = "Предварительный балл, до психометрической калибровки.";

    /** What was computed; {@code scored} is false when the session is not ready yet. */
    public record Result(UUID sessionId, boolean scored, BigDecimal preliminaryScore) {
    }

    private final InviteRepository invites;
    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final TelemetryBatchRepository batches;
    private final TaskFileRepository files;
    private final RunJobRepository runs;
    private final RunResultRepository results;
    private final SessionIndicatorsRepository indicatorRows;
    private final SessionScoreRepository scores;
    private final ScoringProperties scoring;
    private final IndicatorProperties indicatorProperties;
    private final AuditLogRepository audit;
    private final IndicatorCalculator calculator;
    private final TrustRules rules;
    private final ObjectMapper json;
    private final Clock clock;

    public ScoringService(InviteRepository invites, AssessmentSessionRepository sessions, SessionTaskRepository sessionTasks,
                          TelemetryBatchRepository batches, TaskFileRepository files, RunJobRepository runs,
                          RunResultRepository results, SessionIndicatorsRepository indicatorRows,
                          SessionScoreRepository scores, IndicatorProperties indicators, ScoringProperties scoring,
                          AuditLogRepository audit, ObjectMapper json, Clock clock) {
        this.invites = invites;
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.batches = batches;
        this.files = files;
        this.runs = runs;
        this.results = results;
        this.indicatorRows = indicatorRows;
        this.scores = scores;
        this.scoring = scoring;
        this.indicatorProperties = indicators;
        this.audit = audit;
        this.calculator = new IndicatorCalculator(indicators);
        this.rules = new TrustRules(indicators.rules());
        this.json = json;
        this.clock = clock;
    }

    /** Recalculation requested by an administrator; recorded in the audit log in the same transaction. */
    @Transactional
    public Result recomputeByAdmin(UUID sessionId, String actor) {
        Result result = compute(sessionId);
        if (result.scored()) {
            audit.save(new AuditLog(actor, "SCORING_RECOMPUTED", "assessment_session", sessionId, "{}",
                    clock.instant()));
        }
        return result;
    }

    /**
     * Computes (or recomputes) indicators and score of a finished session whose submits are all checked. A session
     * still running or with submits in the queue is left for later; a submit waiting longer than
     * {@code stuck-submit-after} is taken as lost and its task is left out of the score.
     */
    @Transactional
    public Result compute(UUID sessionId) {
        AssessmentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No session " + sessionId));
        // the trigger after finish, the scheduler and an administrator never compute one session at the same time
        invites.lockById(session.getInvite().getId());
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                || runs.existsBySessionTaskSessionIdAndModeAndStatusInAndCreatedAtAfter(sessionId, RunMode.SUBMIT,
                ACTIVE, stuckBefore())) {
            return new Result(sessionId, false, null);
        }
        List<SessionTask> tasks = sessionTasks.findBySessionIdOrderByOrderNo(sessionId);
        Instant now = clock.instant();
        Double baseSpeed = null;
        // the calibration block first: its typing speed is the baseline of the others
        List<SessionTask> ordered = new ArrayList<>(tasks);
        ordered.sort((a, b) -> Boolean.compare(a.getKind() != TaskKind.CALIBRATION, b.getKind() != TaskKind.CALIBRATION));
        for (SessionTask task : ordered) {
            boolean calibration = task.getKind() == TaskKind.CALIBRATION;
            var computed = indicators(task, calibration ? null : baseSpeed);
            if (calibration && computed.burstMax() > 0) {
                baseSpeed = computed.burstMax();
            }
            TrustRules.Verdict verdict = rules.evaluate(computed.values());
            Map<String, IndicatorTexts.Entry> texts = IndicatorTexts.of(computed, baseSpeed, verdict, calibration,
                    indicatorProperties);
            if (calibration) {
                // part 1 of the warm-up: how closely the sample was retyped, and whether it was pasted
                retyping(task).ifPresent(result -> texts.put("retyping", new IndicatorTexts.Entry(Map.of(
                        "similarityPercent", result.similarityPercent(), "passed", result.passed(),
                        "pasteSuspected", result.pasteSuspected()), result.message())));
            }
            String stored = write(texts);
            indicatorRows.findBySessionTaskId(task.getId()).ifPresentOrElse(
                    row -> row.recompute(stored, verdict.level(), now),
                    () -> indicatorRows.save(new SessionIndicators(task, stored, verdict.level(), now)));
        }
        BigDecimal score = score(session, tasks, now);
        LOG.info("Session {} scored: preliminary score {}", sessionId, score);
        return new Result(sessionId, true, score);
    }

    private IndicatorCalculator.TaskIndicators indicators(SessionTask task, Double baseSpeed) {
        List<InputEvent> events = new ArrayList<>();
        for (TelemetryBatch batch : batches.findBySessionTaskIdOrderBySeq(task.getId())) {
            read(batch.getEvents()).forEach(node -> events.add(InputEvent.of(node)));
        }
        List<RunJob> jobs = runs.findBySessionTaskIdOrderByCreatedAt(task.getId());
        List<RunJob> runJobs = jobs.stream().filter(job -> job.getMode() == RunMode.RUN).toList();
        Double firstRun = runJobs.isEmpty() || task.getStartedAt() == null ? null
                : Duration.between(task.getStartedAt(), runJobs.get(0).getCreatedAt()).toMillis() / 1000.0;
        return calculator.compute(events, finalCodeChars(task, jobs), baseSpeed, firstRun, runJobs.size());
    }

    /** Size of the code that was checked (characters of all editable files). */
    private int finalCodeChars(SessionTask task, List<RunJob> jobs) {
        return finalCode(task, jobs).values().stream().mapToInt(String::length).sum();
    }

    /** The code that was checked: the last SUBMIT payload, else the last saved snapshot. */
    private Map<String, String> finalCode(SessionTask task, List<RunJob> jobs) {
        return jobs.stream().filter(job -> job.getMode() == RunMode.SUBMIT)
                .reduce((first, second) -> second)
                .map(job -> readCode(job.getPayload()))
                .orElseGet(() -> TaskCode.current(task.getCurrentCode(),
                        files.findByVariantIdAndKindIn(task.getVariant().getId(), List.of(FileKind.STARTER))));
    }

    /** The retyping check of a warm-up task on its final code and telemetry; empty without retyping files. */
    private Optional<RetypingCheck.Result> retyping(SessionTask task) {
        var retypingFiles = CalibrationFiles.of(files.findByVariantIdAndKindIn(task.getVariant().getId(),
                List.of(FileKind.STARTER, FileKind.READONLY)));
        if (retypingFiles.isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> events = new ArrayList<>();
        for (TelemetryBatch batch : batches.findBySessionTaskIdOrderBySeq(task.getId())) {
            read(batch.getEvents()).forEach(events::add);
        }
        String typed = finalCode(task, runs.findBySessionTaskIdOrderByCreatedAt(task.getId()))
                .getOrDefault(retypingFiles.get().typing().getPath(), "");
        return Optional.of(RetypingCheck.evaluate(retypingFiles.get().sample().getContent(), typed,
                retypingFiles.get().largestPaste(events)));
    }

    /** Submits created before this and still not checked are taken as lost. */
    private Instant stuckBefore() {
        return clock.instant().minus(scoring.stuckSubmitAfter());
    }

    /**
     * Preliminary score 0-100: for each task the share of the tests its last submit had to fix ({@link TaskOutcome}),
     * weighted by the task's level; the warm-up with {@code calibration-weight}, by the tests of its part 2. A
     * submit that did not reach the tests because of the code (compile error, timeout) counts as none passed; one
     * the platform failed to check (runner error, lost) leaves the task out of the score.
     */
    private BigDecimal score(AssessmentSession session, List<SessionTask> tasks, Instant now) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<Map<String, Object>> perTask = new ArrayList<>();
        for (SessionTask task : tasks) {
            boolean calibration = task.getKind() == TaskKind.CALIBRATION;
            BigDecimal weight = calibration ? scoring.calibrationWeight()
                    : scoring.weights().getOrDefault(task.getVariant().getLevel(), BigDecimal.ONE);
            var submit = runs.findFirstBySessionTaskIdAndModeOrderByCreatedAtDesc(task.getId(), RunMode.SUBMIT);
            if (submit.isPresent() && notChecked(submit.get())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionTaskId", task.getId());
                row.put("level", task.getVariant().getLevel());
                row.put("weight", weight);
                row.put("excluded", "Решение не проверено из-за сбоя платформы — задача не входит в балл.");
                perTask.add(row);
                continue;
            }
            TaskOutcome outcome = outcome(task, submit.orElse(null));
            BigDecimal share = outcome.share();
            weighted = weighted.add(share.multiply(weight));
            totalWeight = totalWeight.add(weight);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionTaskId", task.getId());
            row.put("kind", task.getKind());
            row.put("level", task.getVariant().getLevel());
            row.put("weight", weight);
            row.put("testsCounted", outcome.counted());
            row.put("testsCountedPassed", outcome.countedPassed());
            row.put("guardTests", outcome.guards());
            row.put("guardTestsBroken", outcome.guardsBroken());
            row.put("codeUnchanged", outcome.unchanged());
            row.put("share", share);
            if (outcome.zeroReason() != null) {
                row.put("note", outcome.zeroReason());
            }
            perTask.add(row);
        }
        BigDecimal score = totalWeight.signum() == 0 ? BigDecimal.ZERO
                : weighted.multiply(BigDecimal.valueOf(100)).divide(totalWeight, 2, RoundingMode.HALF_UP);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("note", SCORE_NOTE);
        details.put("tasks", perTask);
        String stored = write(details);
        scores.findBySessionId(session.getId()).ifPresentOrElse(
                row -> row.recompute(stored, score, now),
                () -> scores.save(new SessionScore(session, stored, score, now)));
        return score;
    }

    /** The last submit of a task against the tests it had to fix; no submit — nothing passed. */
    public TaskOutcome outcome(SessionTask task, RunJob submit) {
        var variant = task.getVariant();
        JsonNode testCases = submit == null ? null
                : results.findByRunJobId(submit.getId()).map(r -> read(r.getTestCases())).orElse(null);
        boolean unchanged = false;
        if (submit != null && task.getKind() != TaskKind.CALIBRATION) {
            Map<String, String> starter = new LinkedHashMap<>();
            files.findByVariantIdAndKindIn(variant.getId(), List.of(FileKind.STARTER)).stream()
                    .filter(TaskFile::isEditable)
                    .forEach(file -> starter.put(file.getPath(), file.getContent()));
            unchanged = TaskOutcome.unchanged(readCode(submit.getPayload()), starter);
        }
        JsonNode report = variant.getValidationReport() == null ? null : read(variant.getValidationReport());
        return TaskOutcome.of(task.getKind(), testCases, TaskOutcome.guardKeys(report), unchanged);
    }

    /** The platform, not the candidate, failed: a runner error, or a submit still waiting after the stuck limit. */
    private boolean notChecked(RunJob submit) {
        return submit.getStatus() == RunStatus.ERROR
                || (ACTIVE.contains(submit.getStatus()) && submit.getCreatedAt().isBefore(stuckBefore()));
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value == null ? "[]" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is not valid", e);
        }
    }

    private Map<String, String> readCode(String payload) {
        try {
            return json.readValue(payload, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored run payload is not valid JSON", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Indicators are always serializable", e);
        }
    }
}
