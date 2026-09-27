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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;

import ru.gits.api.support.ApiTest;
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
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
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
@Import(SessionApiTest.ClockConfig.class)
class SessionApiTest extends ApiTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");
    private static volatile boolean bankLoaded;

    /** Clock the test moves by hand; replaces the system clock for the whole context of this class. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);

        void set(Instant instant) {
            now.set(instant);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MutableClock clock;
    @Autowired SessionService sessionService;
    @Autowired TaskTemplateRepository templates;
    @Autowired TaskVariantRepository variants;
    @Autowired TaskFileRepository taskFiles;
    @Autowired SessionTaskRepository sessionTasks;
    @Autowired AssessmentSessionRepository sessions;
    @Autowired RunJobRepository runs;
    @Autowired RunResultRepository results;
    @Autowired InviteRepository invites;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void loadBankOnce() throws IOException {
        clock.set(START);
        if (bankLoaded) {
            return;
        }
        Path tasks = Files.createTempDirectory("gits-session-bank").resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), tasks.resolve("schema"));
        copyTree(REPO_TASKS.resolve("java/CAL-calibration"), tasks.resolve("java/CAL-calibration"));
        // Levels that allow both MIDDLE compositions: junior+middle+senior and middle x3
        copyExample(tasks, "T01", "junior");
        copyExample(tasks, "T02", "middle");
        copyExample(tasks, "T03", "senior");
        copyExample(tasks, "T04", "middle");
        copyExample(tasks, "T05", "middle");
        var summary = new TaskBankLoader(templates, variants, transactionManager, Clock.systemUTC(), Set.of())
                .load(tasks.resolve("java"));
        assertThat(summary.loaded()).as(summary.warnings().toString()).isEqualTo(8);
        bankLoaded = true;
    }

    // ---------------------------------------------------------------------------------------------------------------

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

        // Calibration block: its editable files are Typing and the part-2 class
        UUID calibrationId = id(list.get(0));
        JsonNode calibration = read(candidate.get("/candidate/tasks/" + calibrationId).andExpect(status().isOk())
                .andReturn().getResponse());
        assertThat(calibration.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(calibration.get("code").fieldNames()).toIterable().anyMatch(path -> path.endsWith("Typing.java"));

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
            UUID variantId = sessionTasks.findById(sessionTaskId).orElseThrow().getVariant().getId();
            List<TaskFile> secret = taskFiles.findByVariantIdAndKindIn(variantId,
                    List.of(FileKind.SOLUTION, FileKind.HIDDEN_TEST));
            // Lines that exist only in the solution or hidden tests; shared lines (package, imports) prove nothing
            Set<String> visibleLines = taskFiles.findByVariantIdAndKindIn(variantId,
                            List.of(FileKind.STARTER, FileKind.READONLY, FileKind.VISIBLE_TEST)).stream()
                    .flatMap(file -> file.getContent().lines()).map(String::strip).collect(Collectors.toSet());
            assertThat(secret).as("the variant has secret files to check").isNotEmpty();
            for (TaskFile file : secret) {
                List<String> secretLines = file.getContent().lines().map(String::strip)
                        .filter(line -> line.length() > 20 && !visibleLines.contains(line)).toList();
                for (String line : secretLines) {
                    assertThat(response).as("%s must not leak", file.getPath())
                            .doesNotContain(json.writeValueAsString(line).replaceAll("^\"|\"$", ""));
                }
                if (file.getKind() == FileKind.HIDDEN_TEST) {
                    assertThat(response).doesNotContain(file.getPath());
                }
            }
            assertThat(response).doesNotContain("HIDDEN_TEST").doesNotContain("SOLUTION")
                    .doesNotContain("validation").doesNotContain("content_hash").doesNotContain("contentHash");
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
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(SessionStatus.EXPIRED);
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

    // ---------------------------------------------------------------------------------------------------------------

    /** Candidate who entered through an invite link and accepted consent. */
    private Candidate newCandidate() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        Cookie cookie = candidateCookie(mvc.perform(post("/candidate/enter").with(csrf()).with(client())
                .contentType(MediaType.APPLICATION_JSON).content(body("token", token))).andReturn().getResponse());
        MockHttpServletResponse consent = mvc.perform(post("/candidate/consent").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        return new Candidate(candidateCookie(consent));
    }

    private final class Candidate {
        private final Cookie cookie;

        Candidate(Cookie cookie) {
            this.cookie = cookie;
        }

        ResultActions get(String path) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .cookie(cookie));
        }

        ResultActions post(String path) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .cookie(cookie).with(csrf()));
        }

        ResultActions put(String path, Map<String, String> files) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                    .cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("files", files))));
        }
    }

    private InviteStatus inviteStatus(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                sessions.findById(sessionId).orElseThrow().getInvite().getStatus());
    }

    /** Does what the runner does when a job is finished. */
    private void complete(UUID runId, int total, int passed, String cases) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RunJob job = runs.findById(runId).orElseThrow();
            job.markRunning("test-runner", clock.instant());
            job.finish(RunStatus.DONE, clock.instant());
            results.save(new RunResult(job, true, null, total, passed, cases, 120L, "secret-stdout", null,
                    clock.instant()));
        });
    }

    private static String visibleCases() {
        return """
                [{"name":"GradeStatisticsVisibleTest › passes","status":"PASSED","message":null,"hidden":false},
                 {"name":"GradeStatisticsVisibleTest › fails","status":"FAILED","message":"expected: <1>","hidden":false}]
                """;
    }

    private static String hiddenCases() {
        return """
                [{"name":"GradeStatisticsVisibleTest › passes","status":"PASSED","message":null,"hidden":false},
                 {"name":"Скрытый тест 1","status":"PASSED","message":null,"hidden":true},
                 {"name":"Скрытый тест 2","status":"FAILED","message":"expected: <5>","hidden":true}]
                """;
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private static UUID id(JsonNode node) {
        return id(node, "id");
    }

    private static UUID id(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asText());
    }

    /** Copy of T00-example registered as template {@code code} with the given level, re-hashed like validate. */
    private static void copyExample(Path tasks, String code, String level) throws IOException {
        Path template = tasks.resolve("java/" + code + "-sample");
        copyTree(REPO_TASKS.resolve("java/T00-example"), template);
        Path templateYaml = template.resolve("template.yaml");
        Files.writeString(templateYaml, Files.readString(templateYaml).replace("code: T00", "code: " + code));
        Path variant = template.resolve("variants/v01");
        Path taskYaml = variant.resolve("task.yaml");
        Files.writeString(taskYaml, Files.readString(taskYaml)
                .replace("code: T00-v01", "code: " + code + "-v01")
                .replace("template: T00", "template: " + code)
                .replace("level: junior", "level: " + level));
        ValidationReport old = ReportFiles.read(variant).orElseThrow();
        String hash = ContentHash.of(VariantSources.read(variant).allFiles());
        ReportFiles.write(variant, new ValidationReport(code + "-v01", old.status(), hash, old.validatedAt(),
                old.validatorVersion(), old.runs(), old.checks()));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
