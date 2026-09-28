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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.api.session.TaskCode;
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
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Indicators of every task and the preliminary score of a finished session (docs/indicators.md). Runs once all its
 * submits are checked; an administrator can recompute it. The calibration block gets indicators — its typing speed
 * is the candidate's baseline — but takes part neither in the trust level nor in the score.
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
    private final IndicatorCalculator calculator;
    private final TrustRules rules;
    private final ObjectMapper json;
    private final Clock clock;

    public ScoringService(InviteRepository invites, AssessmentSessionRepository sessions, SessionTaskRepository sessionTasks,
                          TelemetryBatchRepository batches, TaskFileRepository files, RunJobRepository runs,
                          RunResultRepository results, SessionIndicatorsRepository indicatorRows,
                          SessionScoreRepository scores, IndicatorProperties indicators, ScoringProperties scoring,
                          ObjectMapper json, Clock clock) {
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
        this.calculator = new IndicatorCalculator(indicators);
        this.rules = new TrustRules(indicators.rules());
        this.json = json;
        this.clock = clock;
    }

    /**
     * Computes (or recomputes) indicators and score of a finished session whose runs are all checked. A session that
     * is still running or has runs in the queue is left for later.
     */
    @Transactional
    public Result compute(UUID sessionId) {
        AssessmentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No session " + sessionId));
        // the trigger after finish, the scheduler and an administrator never compute one session at the same time
        invites.lockById(session.getInvite().getId());
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                || runs.existsBySessionTaskSessionIdAndStatusIn(sessionId, ACTIVE)) {
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
            String stored = write(IndicatorTexts.of(computed, baseSpeed, verdict, calibration));
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

    /** Size of the code that was checked: the last SUBMIT payload, else the last saved snapshot. */
    private int finalCodeChars(SessionTask task, List<RunJob> jobs) {
        Map<String, String> code = jobs.stream().filter(job -> job.getMode() == RunMode.SUBMIT)
                .reduce((first, second) -> second)
                .map(job -> readCode(job.getPayload()))
                .orElseGet(() -> TaskCode.current(task.getCurrentCode(),
                        files.findByVariantIdAndKindIn(task.getVariant().getId(), List.of(FileKind.STARTER))));
        return code.values().stream().mapToInt(String::length).sum();
    }

    /**
     * Preliminary score 0-100: the share of hidden tests passed by the last submit of each task, weighted by the
     * task's level. A submit that did not reach the tests (compile error, timeout) counts as none passed.
     */
    private BigDecimal score(AssessmentSession session, List<SessionTask> tasks, Instant now) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<Map<String, Object>> perTask = new ArrayList<>();
        for (SessionTask task : tasks) {
            if (task.getKind() == TaskKind.CALIBRATION) {
                continue;
            }
            BigDecimal weight = scoring.weights().getOrDefault(task.getVariant().getLevel(), BigDecimal.ONE);
            int passed = 0;
            int total = 0;
            var submit = runs.findFirstBySessionTaskIdAndModeOrderByCreatedAtDesc(task.getId(), RunMode.SUBMIT);
            var result = submit.flatMap(job -> results.findByRunJobId(job.getId()));
            if (result.isPresent()) {
                for (JsonNode testCase : read(result.get().getTestCases())) {
                    if (testCase.path("hidden").asBoolean(false)) {
                        total++;
                        if ("PASSED".equals(testCase.path("status").asText())) {
                            passed++;
                        }
                    }
                }
            }
            BigDecimal share = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            weighted = weighted.add(share.multiply(weight));
            totalWeight = totalWeight.add(weight);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionTaskId", task.getId());
            row.put("level", task.getVariant().getLevel());
            row.put("weight", weight);
            row.put("hiddenTestsPassed", passed);
            row.put("hiddenTestsTotal", total == 0 ? null : total);
            row.put("share", share);
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
