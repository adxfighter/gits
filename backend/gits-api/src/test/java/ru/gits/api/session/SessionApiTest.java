package ru.gits.api.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;

import ru.gits.api.support.CandidateSessionTest;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.taskbank.ContentHash;
import ru.gits.taskbank.ReportFiles;
import ru.gits.taskbank.ValidationReport;
import ru.gits.taskbank.VariantSources;
import ru.gits.taskbank.load.TaskBankLoader;
/**
 * P07 acceptance: the whole candidate path on a bank built from copies of T00-example at the needed levels plus the
 * calibration block, time expiry, run limits and the security of task and SUBMIT responses. The runner is not
 * part of the api: tests finish jobs themselves, the way the runner writes run_result.
 */
class SessionApiTest extends CandidateSessionTest {

    @Test
    void candidatePassesTheWholeSession() throws Exception {
        Candidate candidate = newCandidate();

        JsonNode session = read(candidate.post("/candidate/session/start").andExpect(status().isOk()).andReturn()
                .getResponse());
        List<JsonNode> list = list(session.get("tasks"));
        assertThat(list).hasSize(4);
        assertThat(list.get(0).get("kind").asText()).isEqualTo("CALIBRATION");
        assertThat(list.subList(1, 4)).allSatisfy(task -> assertThat(task.get("kind").asText()).isEqualTo("TASK"));
        assertThat(session.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(session.get("remainingSeconds").asLong()).isEqualTo(90 * 60);

        // Calibration block: its editable files are the retyping text (Typing.txt) and the part-2 class
        UUID calibrationId = id(list.get(0));
        JsonNode calibration = read(candidate.get("/candidate/tasks/" + calibrationId).andExpect(status().isOk())
                .andReturn().getResponse());
        assertThat(calibration.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(calibration.get("code").fieldNames()).toIterable().anyMatch(path -> path.endsWith("Typing.txt"));

        // First task: save code, RUN, see the visible test results
        UUID firstTask = id(list.get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + firstTask).andReturn().getResponse());
        String editablePath = task.get("code").fieldNames().next();
        candidate.put("/candidate/tasks/" + firstTask + "/code", Map.of(editablePath, "// my change\n"))
                .andExpect(status().isNoContent());
        UUID runId = id(read(candidate.post("/candidate/tasks/" + firstTask + "/run")
                .andExpect(status().isAccepted()).andReturn().getResponse()), "runId");
        assertThat(runs.findById(runId).orElseThrow().getPayload()).contains("my change");
        complete(runId, 2, 1, visibleCases());
        JsonNode run = read(candidate.get("/candidate/runs/" + runId).andExpect(status().isOk()).andReturn()
                .getResponse());
        assertThat(run.get("status").asText()).isEqualTo("DONE");
        assertThat(run.get("testsPassed").asInt()).isEqualTo(1);
        assertThat(list(run.get("tests"))).extracting(t -> t.get("name").asText())
                .containsExactly("GradeStatisticsVisibleTest › passes", "GradeStatisticsVisibleTest › fails");

        // SUBMIT all three tasks
        for (JsonNode summary : list.subList(1, 4)) {
            UUID taskId = id(summary);
            UUID submitId = id(read(candidate.post("/candidate/tasks/" + taskId + "/submit")
                    .andExpect(status().isAccepted()).andReturn().getResponse()), "runId");
            complete(submitId, 5, 4, hiddenCases());
            JsonNode submitted = read(candidate.get("/candidate/runs/" + submitId).andReturn().getResponse());
            assertThat(submitted.get("mode").asText()).isEqualTo("SUBMIT");
            assertThat(submitted.get("testsPassed").asInt()).isEqualTo(4);
            assertThat(submitted.get("testsTotal").asInt()).isEqualTo(5);
        }

        JsonNode finished = read(candidate.post("/candidate/session/finish").andExpect(status().isOk()).andReturn()
                .getResponse());
        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(list(finished.get("tasks")))
                .allSatisfy(t -> assertThat(t.get("status").asText()).isEqualTo("SUBMITTED"));
        UUID sessionId = id(finished);
        // the calibration block was submitted automatically on finish
        assertThat(runs.findFirstBySessionTaskIdOrderByCreatedAtDesc(calibrationId).orElseThrow().getMode())
                .isEqualTo(RunMode.SUBMIT);
        assertThat(inviteStatus(sessionId)).isEqualTo(InviteStatus.COMPLETED);
        // the completed invite no longer opens the candidate API
        candidate.get("/candidate/session").andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedStartReturnsTheSameSession() throws Exception {
        Candidate candidate = newCandidate();

        JsonNode first = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        JsonNode second = read(candidate.post("/candidate/session/start").andReturn().getResponse());

        assertThat(id(second)).isEqualTo(id(first));
        assertThat(list(second.get("tasks"))).extracting(t -> t.get("id").asText())
                .containsExactlyElementsOf(list(first.get("tasks")).stream().map(t -> t.get("id").asText()).toList());
    }

    @Test
    void taskResponseNeverContainsSolutionOrHiddenTests() throws Exception {
        Candidate candidate = newCandidate();
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());

        for (JsonNode summary : list(session.get("tasks"))) {
            UUID sessionTaskId = id(summary);
            String response = candidate.get("/candidate/tasks/" + sessionTaskId).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertNoSecrets(response, sessionTasks.findById(sessionTaskId).orElseThrow().getVariant().getId());
        }
    }

    @Test
    void submitResultShowsOnlyTheNumberOfPassedHiddenTests() throws Exception {
        Candidate candidate = newCandidate();
        UUID taskId = id(list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks")).get(1));
        UUID submitId = id(read(candidate.post("/candidate/tasks/" + taskId + "/submit").andReturn().getResponse()),
                "runId");
        complete(submitId, 5, 3, hiddenCases());

        String response = candidate.get("/candidate/runs/" + submitId).andReturn().getResponse().getContentAsString();

        JsonNode run = json.readTree(response);
        assertThat(run.get("testsPassed").asInt()).isEqualTo(3);
        assertThat(list(run.get("tests"))).isEmpty();
        assertThat(run.get("compileOutput").isNull()).isTrue();
        assertThat(run.get("output").isNull()).isTrue();
        assertThat(response).doesNotContain("Скрытый тест").doesNotContain("expected: <5>")
                .doesNotContain("GradeStatisticsHiddenTest").doesNotContain("secret-stdout");
    }

    @Test
    void codeSnapshotRules() throws Exception {
        Candidate candidate = newCandidate();
        UUID taskId = id(list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks")).get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
        String editable = task.get("code").fieldNames().next();
        String readonly = list(task.get("files")).stream().filter(f -> !f.get("editable").asBoolean())
                .map(f -> f.get("path").asText()).findFirst().orElseThrow();

        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(readonly, "hacked"))
                .andExpect(status().isBadRequest());
        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of("src/main/java/Evil.java", "class Evil {}"))
                .andExpect(status().isBadRequest());
        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "x".repeat(300 * 1024)))
                .andExpect(status().isPayloadTooLarge());

        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "// v1\n"))
                .andExpect(status().isNoContent());
        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "// v2\n"))
                .andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofSeconds(5));
        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "// v3\n"))
                .andExpect(status().isNoContent());

        JsonNode saved = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
        assertThat(saved.get("code").get(editable).asText()).isEqualTo("// v3\n");
    }

    @Test
    void oneActiveRunAtATimeAndSixtyRunsPerTask() throws Exception {
        Candidate candidate = newCandidate();
        List<JsonNode> list = list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks"));
        UUID taskId = id(list.get(1));
        UUID otherTask = id(list.get(2));

        UUID first = id(read(candidate.post("/candidate/tasks/" + taskId + "/run").andReturn().getResponse()), "runId");
        candidate.post("/candidate/tasks/" + otherTask + "/run").andExpect(status().isConflict());
        candidate.post("/candidate/tasks/" + taskId + "/submit").andExpect(status().isConflict());
        complete(first, 2, 2, visibleCases());

        for (int i = 1; i < 60; i++) {
            UUID run = id(read(candidate.post("/candidate/tasks/" + taskId + "/run").andExpect(status().isAccepted())
                    .andReturn().getResponse()), "runId");
            complete(run, 2, 2, visibleCases());
        }
        candidate.post("/candidate/tasks/" + taskId + "/run").andExpect(status().isTooManyRequests());
        // the limit is per task: another task can still be run
        candidate.post("/candidate/tasks/" + otherTask + "/run").andExpect(status().isAccepted());
    }

    @Test
    void submittedTaskIsClosedForChanges() throws Exception {
        Candidate candidate = newCandidate();
        UUID taskId = id(list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks")).get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
        String editable = task.get("code").fieldNames().next();
        UUID submitId = id(read(candidate.post("/candidate/tasks/" + taskId + "/submit").andReturn().getResponse()),
                "runId");
        complete(submitId, 5, 5, hiddenCases());

        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "late"))
                .andExpect(status().isConflict());
        candidate.post("/candidate/tasks/" + taskId + "/run").andExpect(status().isConflict());
        candidate.post("/candidate/tasks/" + taskId + "/submit").andExpect(status().isConflict());
    }

    @Test
    void expiredSessionIsSubmittedAutomatically() throws Exception {
        Candidate candidate = newCandidate();
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        UUID taskId = id(list(session.get("tasks")).get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
        String editable = task.get("code").fieldNames().next();
        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "// last version\n"))
                .andExpect(status().isNoContent());

        clock.advance(Duration.ofMinutes(90));

        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(editable, "too late"))
                .andExpect(status().isConflict());
        assertThat(read(candidate.get("/candidate/session").andReturn().getResponse()).get("remainingSeconds").asLong())
                .isZero();

        assertThat(sessionService.expireOverdue()).isGreaterThanOrEqualTo(1);

        var expired = sessions.findById(sessionId).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(SessionStatus.EXPIRED);
        assertThat(inviteStatus(sessionId)).isEqualTo(InviteStatus.COMPLETED);
        assertThat(sessionTasks.findBySessionIdOrderByOrderNo(sessionId))
                .allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(SessionTaskStatus.SUBMITTED));
        RunJob submit = runs.findFirstBySessionTaskIdOrderByCreatedAtDesc(taskId).orElseThrow();
        assertThat(submit.getMode()).isEqualTo(RunMode.SUBMIT);
        assertThat(submit.getPayload()).contains("last version");
        // a second pass has nothing left to do
        assertThat(sessionService.expireOverdue()).isZero();
        assertThat(runs.findFirstBySessionTaskIdOrderByCreatedAtDesc(taskId).orElseThrow().getId())
                .isEqualTo(submit.getId());
    }

    @Test
    void finishingAfterTheDeadlineIsAnExpiry() throws Exception {
        Candidate candidate = newCandidate();
        candidate.post("/candidate/session/start").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(91));

        JsonNode finished = read(candidate.post("/candidate/session/finish").andExpect(status().isOk()).andReturn()
                .getResponse());

        assertThat(finished.get("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void concurrentRequestsOfOneCandidateAreSerialized() throws Exception {
        Candidate candidate = newCandidate();

        List<MockHttpServletResponse> starts = concurrently(() -> candidate.post("/candidate/session/start")
                .andReturn().getResponse());
        Set<UUID> sessionIds = new HashSet<>();
        for (MockHttpServletResponse response : starts) {
            assertThat(response.getStatus()).isEqualTo(200);
            sessionIds.add(id(read(response)));
        }
        assertThat(sessionIds).hasSize(1);

        List<JsonNode> tasks = list(read(starts.get(0)).get("tasks"));
        UUID taskId = id(tasks.get(1));
        List<MockHttpServletResponse> runResponses = concurrently(() -> candidate.post("/candidate/tasks/" + taskId
                + "/run").andReturn().getResponse());
        assertThat(runResponses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(202, 409);
        for (MockHttpServletResponse response : runResponses) {
            if (response.getStatus() == 202) {
                complete(id(read(response), "runId"), 2, 2, visibleCases());
            }
        }

        UUID otherTask = id(tasks.get(2));
        List<MockHttpServletResponse> submits = concurrently(() -> candidate.post("/candidate/tasks/" + otherTask
                + "/submit").andReturn().getResponse());
        assertThat(submits).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(202, 409);
        assertThat(runs.countBySessionTaskIdAndMode(otherTask, RunMode.SUBMIT)).isEqualTo(1);
    }

    @Test
    void recentSessionsAreTakenFromTheSameCompanyOnly() throws Exception {
        var account = accounts.employer();
        MockHttpSession employer = login(account);
        List<Set<String>> given = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            JsonNode session = read(newCandidate(employer).post("/candidate/session/start")
                    .andExpect(status().isOk()).andReturn().getResponse());
            given.add(variantCodes(id(session)));
            clock.advance(Duration.ofMinutes(1));
        }
        newCandidate().post("/candidate/session/start").andExpect(status().isOk());

        Set<String> expected = new HashSet<>();
        given.subList(1, 4).forEach(expected::addAll);
        assertThat(new HashSet<>(sessionTasks.findVariantCodesOfRecentSessions(account.companyId(), 3)))
                .isEqualTo(expected);
        assertThat(new HashSet<>(sessionTasks.findVariantCodesOfRecentSessions(account.companyId(), 1)))
                .isEqualTo(given.get(3));
        assertThat(sessionTasks.findVariantCodesOfRecentSessions(UUID.randomUUID(), 3)).isEmpty();
    }

    @Test
    void candidatesSeeOnlyTheirOwnTasksAndRuns() throws Exception {
        Candidate owner = newCandidate();
        Candidate stranger = newCandidate();
        UUID taskId = id(list(read(owner.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks")).get(1));
        UUID runId = id(read(owner.post("/candidate/tasks/" + taskId + "/run").andReturn().getResponse()), "runId");
        stranger.post("/candidate/session/start").andExpect(status().isOk());

        stranger.get("/candidate/tasks/" + taskId).andExpect(status().isNotFound());
        stranger.post("/candidate/tasks/" + taskId + "/run").andExpect(status().isNotFound());
        stranger.get("/candidate/runs/" + runId).andExpect(status().isNotFound());
    }

    @Test
    void sessionEndpointsNeedConsent() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        Cookie noConsent = candidateCookie(mvc.perform(post("/candidate/enter").with(csrf()).with(client())
                .contentType(MediaType.APPLICATION_JSON).content(body("token", token))).andReturn().getResponse());

        mvc.perform(post("/candidate/session/start").cookie(noConsent).with(csrf())).andExpect(status().isForbidden());
    }

}
