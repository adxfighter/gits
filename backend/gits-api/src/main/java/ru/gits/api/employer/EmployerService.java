package ru.gits.api.employer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.gits.api.scoring.ScoringService;
import ru.gits.api.scoring.TaskOutcome;
import ru.gits.api.security.GitsUserDetails;
import ru.gits.api.session.TaskCode;
import ru.gits.core.audit.AuditLog;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.common.Level;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;
import ru.gits.core.result.SessionIndicators;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionIndicatorsRepository.TaskTrust;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.result.TrustLevel;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Employer dashboard: invites with results, the session report and replay data. Every lookup is limited to the
 * employer's own company; anything else is "not found". SOLUTION and HIDDEN_TEST files are never read here.
 */
@Service
public class EmployerService {

    /** Files the candidate saw; the replay starts from them. */
    private static final List<FileKind> CANDIDATE_FILES = List.of(FileKind.STARTER, FileKind.READONLY,
            FileKind.VISIBLE_TEST);
    /**
     * Telemetry types needed to replay the session: those listed in P09, plus cursor and select, which show the
     * active file and where the candidate looked. Key and resize events are not replayed.
     */
    static final Set<String> REPLAY_TYPES = Set.of("edit", "paste", "focus", "blur", "visibility", "run", "submit",
            "completion", "cursor", "select");
    static final int DEFAULT_REPLAY_BATCHES = 50;
    static final int MAX_REPLAY_BATCHES = 200;

    public record InviteRow(UUID id, String candidateLabel, Level targetLevel, InviteStatus status,
                            Instant createdAt, Instant expiresAt, Instant usedAt, UUID sessionId,
                            SessionStatus sessionStatus, Instant startedAt, Instant finishedAt,
                            BigDecimal preliminaryScore, TrustLevel trustLevel) {
    }

    public record TaskReport(UUID id, int orderNo, TaskKind kind, String templateCode, String templateTitle,
                             List<String> competencies, List<String> competencyTitles,
                             Level level, String title, SessionTaskStatus status,
                             RunStatus submitStatus, Boolean submitCompiled, Integer hiddenTestsPassed,
                             Integer hiddenTestsTotal, TaskOutcome counted, Instant startedAt,
                             Instant submittedAt, Long durationSeconds, long runs, TrustLevel trustLevel,
                             JsonNode indicators, Map<String, String> finalCode) {
    }

    public record SessionReport(UUID sessionId, UUID inviteId, String candidateLabel, Level targetLevel,
                                SessionStatus status, Instant startedAt, Instant finishedAt,
                                BigDecimal preliminaryScore, Instant scoreComputedAt, JsonNode scorePerTask,
                                TrustLevel trustLevel, List<TaskReport> tasks) {
    }

    public record ReplayFile(String path, FileKind kind, boolean editable, String content) {
    }

    /**
     * A run; {@code offsetMs} is its creation time in milliseconds after the task was opened on the server, the
     * approximate place of the run on the timeline of event {@code t}.
     */
    public record ReplayRun(UUID id, RunMode mode, RunStatus status, Instant createdAt, Instant finishedAt,
                            Long offsetMs, Boolean compiled, Integer testsTotal, Integer testsPassed) {
    }

    /**
     * Replay page. {@code events} keep their fields from docs/telemetry.md plus {@code seq} of their batch;
     * {@code nextSeq} is where the next page starts, null on the last page.
     */
    public record Replay(UUID sessionTaskId, UUID sessionId, SessionTaskStatus status, Instant startedAt,
                         Instant submittedAt, List<ReplayFile> initialFiles, List<ReplayRun> runs,
                         List<JsonNode> events, int fromSeq, Integer nextSeq) {
    }

    private final InviteRepository invites;
    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final TaskFileRepository files;
    private final RunJobRepository runs;
    private final RunResultRepository results;
    private final TelemetryBatchRepository batches;
    private final SessionScoreRepository scores;
    private final SessionIndicatorsRepository indicators;
    private final AuditLogRepository audit;
    private final CompetencyCatalog competencyCatalog;
    private final ScoringService scoring;
    private final ObjectMapper json;
    private final Clock clock;

    public EmployerService(InviteRepository invites, AssessmentSessionRepository sessions,
                           SessionTaskRepository sessionTasks, TaskFileRepository files, RunJobRepository runs,
                           RunResultRepository results, TelemetryBatchRepository batches,
                           SessionScoreRepository scores, SessionIndicatorsRepository indicators,
                           AuditLogRepository audit, CompetencyCatalog competencyCatalog, ScoringService scoring,
                           ObjectMapper json,
                           Clock clock) {
        this.invites = invites;
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.files = files;
        this.runs = runs;
        this.results = results;
        this.batches = batches;
        this.scores = scores;
        this.indicators = indicators;
        this.audit = audit;
        this.competencyCatalog = competencyCatalog;
        this.scoring = scoring;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Invites of the company, newest first, optionally of one status, with session state and results. An unused
     * invite past its expiry is shown (and filtered) as EXPIRED even before anyone opened the link.
     */
    @Transactional(readOnly = true)
    public List<InviteRow> invites(UUID companyId, InviteStatus status) {
        Instant now = clock.instant();
        List<Invite> list = invites.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(invite -> status == null || effectiveStatus(invite, now) == status)
                .toList();
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, AssessmentSession> sessionByInvite = sessions.findByInviteIdIn(
                        list.stream().map(Invite::getId).toList()).stream()
                .collect(Collectors.toMap(session -> session.getInvite().getId(), Function.identity()));
        List<UUID> sessionIds = sessionByInvite.values().stream().map(AssessmentSession::getId).toList();
        Map<UUID, BigDecimal> scoreBySession = sessionIds.isEmpty() ? Map.of()
                : scores.findBySessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(score -> score.getSession().getId(), SessionScore::getPreliminaryScore));
        Map<UUID, TrustLevel> trustBySession = sessionIds.isEmpty() ? Map.of()
                : indicators.findTrustOfSessions(sessionIds).stream()
                .filter(trust -> trust.kind() != TaskKind.CALIBRATION)
                .collect(Collectors.toMap(TaskTrust::sessionId, TaskTrust::trustLevel, EmployerService::worse));
        return list.stream().map(invite -> {
            AssessmentSession session = sessionByInvite.get(invite.getId());
            if (session == null) {
                return new InviteRow(invite.getId(), invite.getCandidateLabel(), invite.getTargetLevel(),
                        effectiveStatus(invite, now), invite.getCreatedAt(), invite.getExpiresAt(),
                        invite.getUsedAt(), null, null, null, null, null, null);
            }
            return new InviteRow(invite.getId(), invite.getCandidateLabel(), invite.getTargetLevel(),
                    effectiveStatus(invite, now), invite.getCreatedAt(), invite.getExpiresAt(), invite.getUsedAt(),
                    session.getId(), session.getStatus(), session.getStartedAt(), session.getFinishedAt(),
                    scoreBySession.get(session.getId()), trustBySession.get(session.getId()));
        }).toList();
    }

    /** Report of a session: score, and per task the result on hidden tests, time, runs, indicators, final code. */
    @Transactional(readOnly = true)
    public SessionReport report(UUID companyId, UUID sessionId) {
        AssessmentSession session = sessions.findByIdAndInviteCompanyId(sessionId, companyId)
                .orElseThrow(() -> notFound("Сессия не найдена"));
        Invite invite = session.getInvite();
        var score = scores.findBySessionId(session.getId());
        Map<UUID, SessionIndicators> indicatorsByTask = indicators.findBySessionTaskSessionIdIn(
                        List.of(session.getId())).stream()
                .collect(Collectors.toMap(row -> row.getSessionTask().getId(), Function.identity()));
        List<TaskReport> tasks = new ArrayList<>();
        for (SessionTask task : sessionTasks.findBySessionIdOrderByOrderNo(session.getId())) {
            tasks.add(taskReport(task, indicatorsByTask.get(task.getId())));
        }
        // the calibration block has indicators, but they do not count toward the trust level (P12)
        TrustLevel trust = indicatorsByTask.values().stream()
                .filter(row -> row.getSessionTask().getKind() != TaskKind.CALIBRATION)
                .map(SessionIndicators::getTrustLevel)
                .reduce(EmployerService::worse).orElse(null);
        return new SessionReport(session.getId(), invite.getId(), invite.getCandidateLabel(),
                invite.getTargetLevel(), session.getStatus(), session.getStartedAt(), session.getFinishedAt(),
                score.map(SessionScore::getPreliminaryScore).orElse(null),
                score.map(SessionScore::getComputedAt).orElse(null),
                score.map(value -> read(value.getPerTask())).orElse(null), trust, tasks);
    }

    private TaskReport taskReport(SessionTask task, SessionIndicators row) {
        var variant = task.getVariant();
        var template = variant.getTemplate();
        var submit = runs.findFirstBySessionTaskIdAndModeOrderByCreatedAtDesc(task.getId(), RunMode.SUBMIT);
        Integer hiddenPassed = null;
        Integer hiddenTotal = null;
        var result = submit.flatMap(job -> results.findByRunJobId(job.getId()));
        if (submit.isPresent() && submit.get().getStatus() != RunStatus.QUEUED
                && submit.get().getStatus() != RunStatus.RUNNING) {
            // checked, but the hidden tests may not have run (compile error, timeout, runner error): then none
            // passed, and their number is unknown from this run
            hiddenPassed = 0;
        }
        if (result.isPresent()) {
            List<JsonNode> hidden = new ArrayList<>();
            read(result.get().getTestCases()).forEach(testCase -> {
                if (testCase.path("hidden").asBoolean(false)) {
                    hidden.add(testCase);
                }
            });
            if (!hidden.isEmpty()) {
                hiddenTotal = hidden.size();
                hiddenPassed = (int) hidden.stream().filter(t -> "PASSED".equals(t.path("status").asText()))
                        .count();
            }
        }
        // the code that was checked: the last SUBMIT payload, otherwise the last saved snapshot
        Map<String, String> finalCode = submit.map(job -> code(job.getPayload()))
                .orElseGet(() -> TaskCode.current(task.getCurrentCode(),
                        files.findByVariantIdAndKindIn(variant.getId(), List.of(FileKind.STARTER))));
        Long duration = task.getStartedAt() == null || task.getSubmittedAt() == null ? null
                : Duration.between(task.getStartedAt(), task.getSubmittedAt()).toSeconds();
        List<String> competencies = competencies(template.getCompetencies());
        return new TaskReport(task.getId(), task.getOrderNo(), task.getKind(), template.getCode(),
                template.getTitle(), competencies, competencyCatalog.titles(competencies), variant.getLevel(),
                TaskCode.title(variant), task.getStatus(), submit.map(RunJob::getStatus).orElse(null),
                result.map(RunResult::isCompiled).orElse(null), hiddenPassed, hiddenTotal,
                submit.isPresent() ? scoring.outcome(task, submit.get()) : null, task.getStartedAt(),
                task.getSubmittedAt(), duration, runs.countBySessionTaskIdAndMode(task.getId(), RunMode.RUN),
                row == null ? null : row.getTrustLevel(), row == null ? null : read(row.getIndicators()), finalCode);
    }

    /** Replay data of a task: the files the candidate started from, runs and a page of events by batch seq. */
    @Transactional(readOnly = true)
    public Replay replay(UUID companyId, UUID sessionTaskId, int fromSeq, Integer limit) {
        SessionTask task = sessionTasks.findByIdAndSessionInviteCompanyId(sessionTaskId, companyId)
                .orElseThrow(() -> notFound("Задание не найдено"));
        if (fromSeq < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromSeq не может быть отрицательным");
        }
        int pageSize = limit == null ? DEFAULT_REPLAY_BATCHES : Math.clamp(limit, 1, MAX_REPLAY_BATCHES);
        List<ReplayFile> initial = files.findByVariantIdAndKindIn(task.getVariant().getId(), CANDIDATE_FILES)
                .stream()
                .sorted(Comparator.comparing(file -> file.getKind().ordinal() + ":" + file.getPath()))
                .map(file -> new ReplayFile(file.getPath(), file.getKind(), file.isEditable(), file.getContent()))
                .toList();
        List<RunJob> jobs = runs.findBySessionTaskIdOrderByCreatedAt(task.getId());
        Map<UUID, RunResult> resultByJob = jobs.isEmpty() ? Map.of()
                : results.findByRunJobIdIn(jobs.stream().map(RunJob::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getRunJob().getId(), Function.identity()));
        List<ReplayRun> runViews = jobs.stream().map(job -> {
            RunResult r = resultByJob.get(job.getId());
            Long offset = task.getStartedAt() == null ? null
                    : Duration.between(task.getStartedAt(), job.getCreatedAt()).toMillis();
            return new ReplayRun(job.getId(), job.getMode(), job.getStatus(), job.getCreatedAt(),
                    job.getFinishedAt(), offset, r == null ? null : r.isCompiled(), r == null ? null : r.getTestsTotal(),
                    r == null ? null : r.getTestsPassed());
        }).toList();
        // one batch more than the page tells whether there is a next page
        List<TelemetryBatch> page = batches.findBySessionTaskIdAndSeqGreaterThanEqualOrderBySeq(task.getId(),
                fromSeq, Limit.of(pageSize + 1));
        Integer nextSeq = page.size() > pageSize ? page.get(pageSize).getSeq() : null;
        List<JsonNode> events = new ArrayList<>();
        for (TelemetryBatch batch : page.subList(0, Math.min(page.size(), pageSize))) {
            read(batch.getEvents()).forEach(event -> {
                if (REPLAY_TYPES.contains(event.path("type").asText())) {
                    ObjectNode copy = ((ObjectNode) event).deepCopy();
                    copy.put("seq", batch.getSeq());
                    events.add(copy);
                }
            });
        }
        return new Replay(task.getId(), task.getSession().getId(), task.getStatus(), task.getStartedAt(),
                task.getSubmittedAt(), initial,
                runViews, events, fromSeq, nextSeq);
    }

    /** Revokes an invite nobody has used yet; a used one is kept for its results. */
    @Transactional
    public void revoke(GitsUserDetails employer, UUID inviteId) {
        // locked like the candidate's entry, so a link opened at the same moment is either revoked or used
        Invite invite = invites.lockById(inviteId)
                .filter(found -> found.getCompany().getId().equals(employer.companyId()))
                .orElseThrow(() -> notFound("Приглашение не найдено"));
        if (invite.getStatus() == InviteStatus.REVOKED) {
            return;
        }
        if (invite.getStatus() != InviteStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отозвать можно только неиспользованное приглашение");
        }
        invite.revoke();
        audit.save(new AuditLog(employer.getUsername(), "INVITE_REVOKED", "invite", invite.getId(), "{}",
                clock.instant()));
    }

    /** CREATED past its expiry counts as EXPIRED; the stored status changes when someone opens the link. */
    private static InviteStatus effectiveStatus(Invite invite, Instant now) {
        return invite.getStatus() == InviteStatus.CREATED && invite.isExpired(now) ? InviteStatus.EXPIRED
                : invite.getStatus();
    }

    private static TrustLevel worse(TrustLevel a, TrustLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private List<String> competencies(String value) {
        List<String> list = new ArrayList<>();
        read(value).forEach(node -> list.add(node.asText()));
        return list;
    }

    private Map<String, String> code(String payload) {
        try {
            return json.readValue(payload, new TypeReference<java.util.TreeMap<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored run payload is not valid JSON", e);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value == null ? "[]" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is not valid", e);
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
