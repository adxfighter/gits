package ru.gits.api.session;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import ru.gits.api.security.CurrentUser;

/**
 * Candidate side of the assessment session. Every endpoint requires the candidate cookie with accepted consent
 * (see SecurityConfig) and works only with the candidate's own session.
 */
@RestController
@RequestMapping("/candidate")
class SessionController {

    /** Editable files by path; for run/submit the body is optional and the saved snapshot is used without it. */
    record CodeRequest(@NotNull Map<String, String> files) {
    }

    private final SessionService sessions;
    private final CandidateTaskService tasks;

    SessionController(SessionService sessions, CandidateTaskService tasks) {
        this.sessions = sessions;
        this.tasks = tasks;
    }

    @PostMapping("/session/start")
    SessionService.SessionView start() {
        return sessions.start(inviteId());
    }

    @GetMapping("/session")
    SessionService.SessionView session() {
        return sessions.view(inviteId());
    }

    @PostMapping("/session/finish")
    SessionService.SessionView finish() {
        return sessions.finish(inviteId());
    }

    @GetMapping("/tasks/{sessionTaskId}")
    CandidateTaskService.TaskView task(@PathVariable UUID sessionTaskId) {
        return tasks.task(inviteId(), sessionTaskId);
    }

    @PutMapping("/tasks/{sessionTaskId}/code")
    ResponseEntity<Void> saveCode(@PathVariable UUID sessionTaskId, @Valid @RequestBody CodeRequest body) {
        tasks.saveCode(inviteId(), sessionTaskId, body.files());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/{sessionTaskId}/run")
    ResponseEntity<CandidateTaskService.RunAccepted> run(@PathVariable UUID sessionTaskId,
                                                         @RequestBody(required = false) CodeRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tasks.run(inviteId(), sessionTaskId, files(body)));
    }

    @PostMapping("/tasks/{sessionTaskId}/submit")
    ResponseEntity<CandidateTaskService.RunAccepted> submit(@PathVariable UUID sessionTaskId,
                                                            @RequestBody(required = false) CodeRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tasks.submit(inviteId(), sessionTaskId, files(body)));
    }

    @GetMapping("/runs/{runId}")
    CandidateTaskService.RunView run(@PathVariable UUID runId) {
        return tasks.runView(inviteId(), runId);
    }

    private static Map<String, String> files(CodeRequest body) {
        return body == null || body.files() == null ? Map.of() : body.files();
    }

    private static UUID inviteId() {
        return CurrentUser.candidate().inviteId();
    }
}
