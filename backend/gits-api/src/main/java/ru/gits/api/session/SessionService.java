package ru.gits.api.session;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ru.gits.api.session.selection.TaskProvider;
import ru.gits.api.session.selection.TaskSelectionService;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskVariant;

/**
 * Lifecycle of a candidate's assessment session: start with task selection, state, finish and automatic submission
 * when the time is over. Time is always taken from the server clock.
 */
@Service
public class SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);

    /** One task of the session as the candidate sees it in the list. */
    public record TaskSummary(UUID id, int orderNo, TaskKind kind, String title, SessionTaskStatus status,
                              int timeLimitMin, long runsUsed, int runsLimit) {
    }

    /** Session state; {@code remainingSeconds} is computed on the server. */
    public record SessionView(UUID id, SessionStatus status, Instant startedAt, Instant deadline,
                              long remainingSeconds, List<TaskSummary> tasks) {
    }

    private final InviteRepository invites;
    private final AssessmentSessionRepository sessions;
    private final SessionTaskRepository sessionTasks;
    private final RunJobRepository runs;
    private final TaskFileRepository files;
    private final TaskProvider taskProvider;
    private final TaskSelectionService selection;
    private final SessionProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom seeds = new SecureRandom();

    public SessionService(InviteRepository invites, AssessmentSessionRepository sessions,
                          SessionTaskRepository sessionTasks, RunJobRepository runs, TaskFileRepository files,
                          TaskProvider taskProvider, TaskSelectionService selection, SessionProperties properties,
                          ApplicationEventPublisher events, Clock clock) {
        this.invites = invites;
        this.sessions = sessions;
        this.sessionTasks = sessionTasks;
        this.runs = runs;
        this.files = files;
        this.taskProvider = taskProvider;
        this.selection = selection;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Starts the session of the invite, or returns the running one: a repeated call (reload, second tab) never
     * creates a second session or a new set of tasks.
     */
    @Transactional
    public SessionView start(UUID inviteId) {
        Invite invite = invites.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));
        var existing = sessions.findByInviteId(inviteId);
        if (existing.isPresent()) {
            return view(existing.get());
        }
        long seed = seeds.nextLong();
        var avoid = new HashSet<>(sessionTasks.findVariantCodesOfRecentSessions(invite.getCompany().getId(),
                properties.recentSessions()));
        TaskSelectionService.Selection chosen;
        try {
            chosen = selection.select(invite.getTargetLevel(), seed, taskProvider, avoid);
        } catch (TaskSelectionService.NotEnoughTasksException e) {
            LOG.error("Session for invite {} cannot start: {}", inviteId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Задания для оценки сейчас недоступны, попробуйте позже");
        }
        Instant now = clock.instant();
        var session = sessions.save(new AssessmentSession(invite, (int) properties.timeLimit().toMinutes(), seed, now));
        List<TaskVariant> ordered = new ArrayList<>();
        ordered.add(chosen.calibration());
        ordered.addAll(chosen.tasks());
        for (int i = 0; i < ordered.size(); i++) {
            TaskVariant variant = ordered.get(i);
            sessionTasks.save(new SessionTask(session, variant, i + 1, variant.getKind()));
        }
        LOG.info("Session {} started for invite {}", session.getId(), inviteId);
        return view(session);
    }

    @Transactional(readOnly = true)
    public SessionView view(UUID inviteId) {
        return view(session(inviteId));
    }

    /**
     * Finishes the session: unsubmitted tasks are submitted with their last snapshot, the invite is completed and
     * indicators are computed asynchronously (P12).
     */
    @Transactional
    public SessionView finish(UUID inviteId) {
        AssessmentSession session = session(inviteId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            return view(session);
        }
        close(session, SessionStatus.FINISHED);
        return view(session);
    }

    /** Scheduler entry: sessions whose time is over are submitted and marked EXPIRED. Returns their number. */
    @Transactional
    public int expireOverdue() {
        Instant now = clock.instant();
        int expired = 0;
        for (AssessmentSession session : sessions.findByStatus(SessionStatus.IN_PROGRESS)) {
            if (!now.isBefore(session.deadline())) {
                close(session, SessionStatus.EXPIRED);
                expired++;
                LOG.info("Session {} expired, unfinished tasks submitted automatically", session.getId());
            }
        }
        return expired;
    }

    /** The candidate's session, or 404 when it was not started. */
    AssessmentSession session(UUID inviteId) {
        return sessions.findByInviteId(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия оценки не начата"));
    }

    private void close(AssessmentSession session, SessionStatus status) {
        Instant now = clock.instant();
        for (SessionTask task : sessionTasks.findBySessionIdOrderByOrderNo(session.getId())) {
            if (task.getStatus() != SessionTaskStatus.SUBMITTED) {
                var taskFiles = files.findByVariantIdAndKindIn(task.getVariant().getId(), List.of(FileKind.STARTER));
                String code = TaskCode.json(TaskCode.current(task.getCurrentCode(), taskFiles));
                runs.save(new RunJob(task, RunMode.SUBMIT, code, now));
                task.submit(now);
            }
        }
        if (status == SessionStatus.FINISHED) {
            session.finish(now);
        } else {
            session.expire(now);
        }
        session.getInvite().markCompleted();
        events.publishEvent(new SessionFinishedEvent(session.getId()));
    }

    private SessionView view(AssessmentSession session) {
        Instant now = clock.instant();
        long remaining = session.getStatus() == SessionStatus.IN_PROGRESS
                ? Math.max(0, Duration.between(now, session.deadline()).toSeconds()) : 0;
        List<TaskSummary> tasks = sessionTasks.findBySessionIdOrderByOrderNo(session.getId()).stream()
                .map(task -> new TaskSummary(task.getId(), task.getOrderNo(), task.getKind(),
                        TaskCode.title(task.getVariant()), task.getStatus(), task.getVariant().getTimeLimitMin(),
                        runs.countBySessionTaskIdAndMode(task.getId(), RunMode.RUN), properties.runsPerTask()))
                .toList();
        return new SessionView(session.getId(), session.getStatus(), session.getStartedAt(), session.deadline(),
                remaining, tasks);
    }
}
