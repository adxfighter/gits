package ru.gits.api.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.telemetry.TelemetryBatchRepository;
import ru.gits.taskbank.demo.DemoSession;

/**
 * One finished session as a demo file ({@link DemoSession}, P16): what {@code gits-taskbank demo-seed} loads into
 * a fresh installation. The file keeps the recording — code, runs, telemetry — under the label the admin gives it;
 * the candidate's own label, the token, IP and user agent are not written.
 */
@Service
public class DemoExportService {

    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final RunJobRepository runs;
    private final RunResultRepository results;
    private final TelemetryBatchRepository batches;
    private final ObjectMapper json;

    public DemoExportService(AssessmentSessionRepository sessions, SessionTaskRepository sessionTasks,
                             RunJobRepository runs, RunResultRepository results, TelemetryBatchRepository batches,
                             ObjectMapper json) {
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.runs = runs;
        this.results = results;
        this.batches = batches;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public DemoSession export(UUID sessionId, String label) {
        AssessmentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена"));
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сессия ещё идёт: выгрузить можно завершённую");
        }
        if (label == null || label.isBlank() || label.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужна метка демо-сессии, до 200 символов");
        }
        List<DemoSession.Task> tasks = sessionTasks.findBySessionIdOrderByOrderNo(sessionId).stream()
                .map(this::task).toList();
        return new DemoSession(DemoSession.FORMAT, label.strip(), session.getInvite().getTargetLevel(),
                session.getStatus(), session.getTimeLimitMin(), session.getRandomSeed(), session.getStartedAt(),
                session.getFinishedAt(), tasks);
    }

    private DemoSession.Task task(SessionTask task) {
        List<RunJob> jobs = runs.findBySessionTaskIdOrderByCreatedAt(task.getId());
        Map<UUID, RunResult> resultByJob = jobs.isEmpty() ? Map.of()
                : results.findByRunJobIdIn(jobs.stream().map(RunJob::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getRunJob().getId(), Function.identity()));
        List<DemoSession.Run> runViews = jobs.stream().map(job -> {
            RunResult r = resultByJob.get(job.getId());
            DemoSession.Result result = r == null ? null
                    : new DemoSession.Result(r.isCompiled(), r.getCompileOutput(), r.getTestsTotal(),
                    r.getTestsPassed(), read(r.getTestCases()), r.getDurationMs(), r.getStdoutTrunc(),
                    r.getStderrTrunc(), r.getCreatedAt());
            return new DemoSession.Run(job.getMode(), job.getStatus(), job.getCreatedAt(), job.getStartedAt(),
                    job.getFinishedAt(), read(job.getPayload()), result);
        }).toList();
        List<DemoSession.Batch> telemetry = batches.findBySessionTaskIdOrderBySeq(task.getId()).stream()
                .map(batch -> new DemoSession.Batch(batch.getSeq(), batch.getClientTsStart(), batch.getClientTsEnd(),
                        read(batch.getEvents()), read(batch.getFlags()), batch.getReceivedAt()))
                .toList();
        return new DemoSession.Task(task.getOrderNo(), task.getKind(), task.getVariant().getCode(), task.getStatus(),
                task.getStartedAt(), task.getSubmittedAt(), read(task.getCurrentCode()), task.getCodeSavedAt(),
                runViews, telemetry);
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
}
