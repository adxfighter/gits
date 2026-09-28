package ru.gits.api.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.api.telemetry.BeaconTokens;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * A candidate's work on one task: the task view, code snapshots, RUN and SUBMIT. Only STARTER, READONLY and
 * VISIBLE_TEST files are ever read here; SOLUTION and HIDDEN_TEST stay with the runner.
 */
@Service
public class CandidateTaskService {

    /** Files the candidate may see. The query itself never loads the others. */
    static final List<FileKind> CANDIDATE_FILES = List.of(FileKind.STARTER, FileKind.READONLY, FileKind.VISIBLE_TEST);
    private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING);
    private static final ObjectMapper JSON = new ObjectMapper();

    public record FileView(String path, FileKind kind, boolean editable, String content) {
    }

    public record TaskView(UUID id, int orderNo, TaskKind kind, String title, SessionTaskStatus status,
                           String statementMd, int timeLimitMin, List<FileView> files, Map<String, String> code,
                           Instant codeSavedAt, long runsUsed, int runsLimit, String beaconToken,
                           int telemetryNextSeq, double telemetryLastT) {
    }

    public record RunAccepted(UUID runId, RunMode mode, RunStatus status) {
    }

    /** One test of a RUN; SUBMIT results never list tests. */
    public record TestView(String name, String status, String message) {
    }

    /**
     * Run status and result. For SUBMIT only the counts are filled: no names, messages or compiler output of hidden
     * tests leave the server.
     */
    public record RunView(UUID id, RunMode mode, RunStatus status, Instant createdAt, Instant finishedAt,
                          Boolean compiled, Integer testsTotal, Integer testsPassed, String compileOutput,
                          List<TestView> tests, String output, Long durationMs) {
    }

    /** {@code key} identifies a hidden test for scoring; it is not part of what the candidate sees. */
    private record StoredTestCase(String name, String status, String message, boolean hidden, String key) {
    }

    private final InviteRepository invites;
    private final TelemetryBatchRepository batches;
    private final SessionTaskRepository sessionTasks;
    private final TaskFileRepository files;
    private final RunJobRepository runs;
    private final RunResultRepository results;
    private final SessionProperties properties;
    private final Clock clock;

    public CandidateTaskService(InviteRepository invites, TelemetryBatchRepository batches,
                                SessionTaskRepository sessionTasks, TaskFileRepository files, RunJobRepository runs,
                                RunResultRepository results, SessionProperties properties, Clock clock) {
        this.invites = invites;
        this.batches = batches;
        this.sessionTasks = sessionTasks;
        this.files = files;
        this.runs = runs;
        this.results = results;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The task with its visible files and current code; opening it starts the task. While the task is in progress
     * the view carries a fresh one-time token for a sendBeacon telemetry batch (docs/telemetry.md).
     */
    @Transactional
    public TaskView task(UUID inviteId, UUID sessionTaskId) {
        lock(inviteId);
        SessionTask task = ownTask(inviteId, sessionTaskId);
        // a reloaded page continues the task's telemetry: next seq and the end of its time scale
        var lastBatch = batches.findFirstBySessionTaskIdOrderBySeqDesc(task.getId());
        String beaconToken = null;
        if (isOpen(task.getSession())) {
            task.start(clock.instant());
            if (task.getStatus() == SessionTaskStatus.IN_PROGRESS) {
                beaconToken = BeaconTokens.issue(task);
            }
        }
        List<TaskFile> taskFiles = candidateFiles(task);
        List<FileView> fileViews = taskFiles.stream()
                .map(file -> new FileView(file.getPath(), file.getKind(), TaskCode.isEditable(file), file.getContent()))
                .toList();
        return new TaskView(task.getId(), task.getOrderNo(), task.getKind(), TaskCode.title(task.getVariant()),
                task.getStatus(), task.getVariant().getStatementMd(), task.getVariant().getTimeLimitMin(), fileViews,
                TaskCode.current(task.getCurrentCode(), taskFiles), task.getCodeSavedAt(),
                runs.countBySessionTaskIdAndMode(task.getId(), RunMode.RUN), properties.runsPerTask(), beaconToken,
                lastBatch.map(batch -> batch.getSeq() + 1).orElse(0),
                lastBatch.map(TelemetryBatch::getClientTsEnd).orElse(0.0));
    }

    /** Autosave of the editable files: at most once per {@code autosave-interval}, up to {@code max-code-bytes}. */
    @Transactional
    public void saveCode(UUID inviteId, UUID sessionTaskId, Map<String, String> changed) {
        SessionTask task = editableTask(inviteId, sessionTaskId);
        Instant now = clock.instant();
        if (task.getCodeSavedAt() != null
                && Duration.between(task.getCodeSavedAt(), now).compareTo(properties.autosaveInterval()) < 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Код сохраняется слишком часто");
        }
        store(task, changed, now);
    }

    @Transactional
    public RunAccepted run(UUID inviteId, UUID sessionTaskId, Map<String, String> changed) {
        SessionTask task = editableTask(inviteId, sessionTaskId);
        checkNoActiveRun(task);
        if (runs.countBySessionTaskIdAndMode(task.getId(), RunMode.RUN) >= properties.runsPerTask()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Исчерпан лимит запусков для задачи: " + properties.runsPerTask());
        }
        return enqueue(task, RunMode.RUN, changed);
    }

    /** Final check of the task on hidden tests; afterwards the task is closed for edits. */
    @Transactional
    public RunAccepted submit(UUID inviteId, UUID sessionTaskId, Map<String, String> changed) {
        SessionTask task = editableTask(inviteId, sessionTaskId);
        checkNoActiveRun(task);
        RunAccepted accepted = enqueue(task, RunMode.SUBMIT, changed);
        task.submit(clock.instant());
        return accepted;
    }

    /**
     * Checks the warm-up retyping (part 1) on the platform, without the sandbox: similarity with the sample and a
     * paste into the typing file (docs/api.md). {@code changed} files, if given, are saved first, as for a run.
     */
    @Transactional
    public RetypingCheck.Result checkRetyping(UUID inviteId, UUID sessionTaskId, Map<String, String> changed) {
        SessionTask task = editableTask(inviteId, sessionTaskId);
        List<TaskFile> taskFiles = candidateFiles(task);
        CalibrationFiles retyping = task.getKind() == TaskKind.CALIBRATION
                ? CalibrationFiles.of(taskFiles).orElse(null) : null;
        if (retyping == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В этом задании нет перепечатки");
        }
        Map<String, String> code = changed == null || changed.isEmpty()
                ? TaskCode.current(task.getCurrentCode(), taskFiles)
                : store(task, changed, clock.instant());
        List<JsonNode> events = new ArrayList<>();
        for (TelemetryBatch batch : batches.findBySessionTaskIdOrderBySeq(task.getId())) {
            readTree(batch.getEvents()).forEach(events::add);
        }
        return RetypingCheck.evaluate(retyping.sample().getContent(),
                code.getOrDefault(retyping.typing().getPath(), ""), retyping.largestPaste(events));
    }

    @Transactional(readOnly = true)
    public RunView runView(UUID inviteId, UUID runId) {
        RunJob job = runs.findByIdAndSessionTaskSessionInviteId(runId, inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запуск не найден"));
        var result = results.findByRunJobId(job.getId());
        if (result.isEmpty()) {
            return new RunView(job.getId(), job.getMode(), job.getStatus(), job.getCreatedAt(), job.getFinishedAt(),
                    null, null, null, null, List.of(), null, null);
        }
        RunResult r = result.get();
        if (job.getMode() == RunMode.SUBMIT) {
            return new RunView(job.getId(), job.getMode(), job.getStatus(), job.getCreatedAt(), job.getFinishedAt(),
                    r.isCompiled(), r.getTestsTotal(), r.getTestsPassed(), null, List.of(), null, r.getDurationMs());
        }
        List<TestView> tests = readTestCases(r.getTestCases()).stream()
                .filter(testCase -> !testCase.hidden())
                .map(testCase -> new TestView(testCase.name(), testCase.status(), testCase.message()))
                .toList();
        return new RunView(job.getId(), job.getMode(), job.getStatus(), job.getCreatedAt(), job.getFinishedAt(),
                r.isCompiled(), r.getTestsTotal(), r.getTestsPassed(), r.getCompileOutput(), tests,
                r.getStdoutTrunc(), r.getDurationMs());
    }

    private RunAccepted enqueue(SessionTask task, RunMode mode, Map<String, String> changed) {
        Instant now = clock.instant();
        Map<String, String> code = changed == null || changed.isEmpty()
                ? TaskCode.current(task.getCurrentCode(), candidateFiles(task))
                : store(task, changed, now);
        RunJob job = runs.save(new RunJob(task, mode, TaskCode.json(code), now));
        return new RunAccepted(job.getId(), job.getMode(), job.getStatus());
    }

    /** Merges changed editable files into the snapshot; readonly or unknown paths are rejected. */
    private Map<String, String> store(SessionTask task, Map<String, String> changed, Instant now) {
        List<TaskFile> taskFiles = candidateFiles(task);
        Set<String> editable = taskFiles.stream().filter(TaskCode::isEditable).map(TaskFile::getPath)
                .collect(Collectors.toSet());
        for (var entry : changed.entrySet()) {
            if (!editable.contains(entry.getKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл нельзя изменять: " + entry.getKey());
            }
            if (entry.getValue() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет содержимого файла " + entry.getKey());
            }
        }
        Map<String, String> code = TaskCode.current(task.getCurrentCode(), taskFiles);
        code.putAll(changed);
        if (TaskCode.sizeBytes(code) > properties.maxCodeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Код больше " + properties.maxCodeBytes() / 1024 + " КБ");
        }
        task.saveCode(TaskCode.json(code), now);
        return code;
    }

    private void checkNoActiveRun(SessionTask task) {
        if (runs.existsBySessionTaskSessionIdAndStatusIn(task.getSession().getId(), ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Предыдущий запуск ещё выполняется");
        }
    }

    private SessionTask ownTask(UUID inviteId, UUID sessionTaskId) {
        return sessionTasks.findByIdAndSessionInviteId(sessionTaskId, inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Задание не найдено"));
    }

    /** A task that can still be changed: the session is running, time is not over, the task is not submitted. */
    private SessionTask editableTask(UUID inviteId, UUID sessionTaskId) {
        lock(inviteId);
        SessionTask task = ownTask(inviteId, sessionTaskId);
        if (!isOpen(task.getSession())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Время сессии истекло");
        }
        if (task.getStatus() == SessionTaskStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Задание уже отправлено на проверку");
        }
        task.start(clock.instant());
        return task;
    }

    /**
     * Serializes the candidate's changes with each other and with finish/expiry (see
     * {@link InviteRepository#lockById}): the checks below always see the committed state.
     */
    private void lock(UUID inviteId) {
        invites.lockById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Задание не найдено"));
    }

    private boolean isOpen(AssessmentSession session) {
        return session.getStatus() == SessionStatus.IN_PROGRESS && clock.instant().isBefore(session.deadline());
    }

    private List<TaskFile> candidateFiles(SessionTask task) {
        return files.findByVariantIdAndKindIn(task.getVariant().getId(), CANDIDATE_FILES);
    }

    private static JsonNode readTree(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored telemetry is not valid JSON", e);
        }
    }

    private static List<StoredTestCase> readTestCases(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<StoredTestCase>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored test cases are not valid JSON", e);
        }
    }
}
