package ru.gits.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.api.support.CandidateSessionTest;
import ru.gits.core.common.Level;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunStatus;

/** P12 end to end: a finished session is scored once its submits are checked; the report shows the result. */
class ScoringApiTest extends CandidateSessionTest {

    @Autowired ScoringService scoring;
    @Autowired SessionScoreRepository scores;

    @Test
    void finishedSessionIsScoredWhenItsSubmitsAreCheckedAndTheReportShowsIt() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        Candidate candidate = newCandidate(employer);
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        List<JsonNode> tasks = list(session.get("tasks"));
        UUID calibration = id(tasks.get(0));
        UUID pastedTask = id(tasks.get(1));

        // warm-up typed by hand at 4 chars/s: the candidate's baseline
        candidate.get("/candidate/tasks/" + calibration).andExpect(status().isOk());
        telemetry(candidate, calibration, 0, typing(0, 60, 4));
        // task 1: most of the code pasted
        JsonNode task = read(candidate.get("/candidate/tasks/" + pastedTask).andReturn().getResponse());
        String file = task.get("code").fieldNames().next();
        List<Map<String, Object>> events = new ArrayList<>(typing(0, 100, 5));
        events.add(Map.of("t", 30_000, "type", "paste", "file", file, "length", 500));
        events.add(Map.of("t", 30_001, "type", "edit", "file", file, "rangeOffset", 100, "rangeLength", 0,
                "textLength", 500, "text", "p".repeat(500), "source", "paste"));
        telemetry(candidate, pastedTask, 0, events);
        candidate.put("/candidate/tasks/" + pastedTask + "/code", Map.of(file, "x".repeat(600)))
                .andExpect(status().isNoContent());
        UUID submit = id(read(candidate.post("/candidate/tasks/" + pastedTask + "/submit").andReturn()
                .getResponse()), "runId");
        complete(submit, 3, 2, hiddenCases());
        candidate.post("/candidate/session/finish").andExpect(status().isOk());

        // the automatic submits of the other tasks are still in the queue
        assertThat(scoring.compute(sessionId).scored()).isFalse();
        assertThat(sessions.findIdsReadyForScoring(100, clock.instant().minus(java.time.Duration.ofMinutes(15)))).doesNotContain(sessionId);

        for (UUID queued : queuedSubmits(sessionId)) {
            failToCompile(queued);
        }
        assertThat(sessions.findIdsReadyForScoring(100, clock.instant().minus(java.time.Duration.ofMinutes(15)))).contains(sessionId);
        ScoringService.Result result = scoring.compute(sessionId);
        assertThat(result.scored()).isTrue();

        // one of the tasks passed half of its hidden tests, the others none: weighted by level
        BigDecimal expected = expectedScore(sessionId, pastedTask, new BigDecimal("0.5"));
        assertThat(result.preliminaryScore()).isEqualByComparingTo(expected);

        JsonNode report = read(mvc.perform(get("/employer/sessions/" + sessionId + "/report").session(employer))
                .andExpect(status().isOk()).andReturn().getResponse());
        assertThat(report.get("preliminaryScore").decimalValue()).isEqualByComparingTo(expected);
        assertThat(report.get("scorePerTask").get("note").asText()).contains("до психометрической калибровки");
        assertThat(report.get("trustLevel").asText()).isEqualTo("RED");
        JsonNode pasted = list(report.get("tasks")).stream()
                .filter(t -> t.get("id").asText().equals(pastedTask.toString())).findFirst().orElseThrow();
        assertThat(pasted.get("trustLevel").asText()).isEqualTo("RED");
        JsonNode indicators = pasted.get("indicators");
        assertThat(indicators.get("pasteRatio").get("value").asDouble()).isGreaterThan(0.8);
        assertThat(indicators.get("pasteRatio").get("explanation").asText()).contains("вставкой");
        assertThat(indicators.get("largestPaste").get("value").asInt()).isEqualTo(500);
        assertThat(indicators.get("burstMax").get("explanation").asText()).contains("разминке");
        assertThat(indicators.get("trustReasons").get("value").toString()).contains("Больше половины");
        JsonNode warmUp = list(report.get("tasks")).stream()
                .filter(t -> t.get("id").asText().equals(calibration.toString())).findFirst().orElseThrow();
        assertThat(warmUp.get("indicators").get("burstMax").get("value").asDouble()).isEqualTo(4.0);
        assertThat(warmUp.get("indicators").get("trustReasons").get("explanation").asText()).contains("Разминка");
        // part 1 was not retyped (Typing.txt stayed empty): the check says so, without affecting the trust level
        JsonNode retyping = warmUp.get("indicators").get("retyping");
        assertThat(retyping.get("value").get("passed").asBoolean()).isFalse();
        assertThat(retyping.get("explanation").asText()).startsWith("Напечатанный текст отличается");

        // recomputing replaces the rows instead of adding new ones
        scoring.compute(sessionId);
        assertThat(scores.findAll()).filteredOn(s -> s.getSession().getId().equals(sessionId)).hasSize(1);
    }

    @Test
    void onlyAnAdministratorRecomputesAndOnlyAFinishedSession() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        Candidate candidate = newCandidate(employer);
        UUID sessionId = id(read(candidate.post("/candidate/session/start").andReturn().getResponse()));
        MockHttpSession admin = login(accounts.admin());

        mvc.perform(post("/admin/sessions/" + sessionId + "/scoring").session(employer).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/sessions/" + sessionId + "/scoring").session(admin).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/admin/sessions/" + UUID.randomUUID() + "/scoring").session(admin).with(csrf()))
                .andExpect(status().isNotFound());

        candidate.post("/candidate/session/finish").andExpect(status().isOk());
        for (UUID queued : queuedSubmits(sessionId)) {
            failToCompile(queued);
        }
        JsonNode result = read(mvc.perform(post("/admin/sessions/" + sessionId + "/scoring").session(admin)
                .with(csrf())).andExpect(status().isOk()).andReturn().getResponse());
        assertThat(result.get("scored").asBoolean()).isTrue();
        assertThat(result.get("preliminaryScore").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void aSubmitThePlatformFailedToCheckIsLeftOutOfTheScore() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        Candidate candidate = newCandidate(employer);
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        UUID task = id(list(session.get("tasks")).get(1));
        candidate.get("/candidate/tasks/" + task).andExpect(status().isOk());
        UUID submit = id(read(candidate.post("/candidate/tasks/" + task + "/submit").andReturn().getResponse()),
                "runId");
        complete(submit, 3, 2, hiddenCases());
        candidate.post("/candidate/session/finish").andExpect(status().isOk());
        // queued in task order: the calibration block, task 2, task 3
        List<UUID> queued = queuedSubmits(sessionId);
        assertThat(queued).hasSize(3);
        failToCompile(queued.get(0));
        // task 2 hits a runner error; task 3 stays in the queue after the runner went down
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RunJob job = runs.findById(queued.get(1)).orElseThrow();
            job.markRunning("test-runner", clock.instant());
            job.finish(RunStatus.ERROR, clock.instant());
        });
        assertThat(scoring.compute(sessionId).scored()).isFalse();

        clock.advance(java.time.Duration.ofMinutes(16));
        ScoringService.Result result = scoring.compute(sessionId);

        assertThat(result.scored()).isTrue();
        JsonNode perTask = json.readTree(scores.findAll().stream()
                .filter(s -> s.getSession().getId().equals(sessionId)).findFirst().orElseThrow().getPerTask());
        long excluded = list(perTask.get("tasks")).stream().filter(t -> t.has("excluded")).count();
        assertThat(excluded).isEqualTo(2);
    }

    @Test
    void aTaskScoresTheTestsItHadToFixTheReportSaysSoAndABrokenGuardMakesItZero() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        Candidate candidate = newCandidate(employer);
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        UUID taskId = id(list(session.get("tasks")).get(1));
        // the validator found two hidden cases the starter already passes
        String previous = new TransactionTemplate(transactionManager).execute(status -> {
            var variant = sessionTasks.findById(taskId).orElseThrow().getVariant();
            String old = variant.getValidationReport();
            variant.refreshValidationReport("{\"runs\":{\"starter_passing_hidden\":[\"g1\",\"g2\"]}}", clock.instant());
            return old;
        });
        try {
            JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
            String file = task.get("code").fieldNames().next();
            candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(file, "class Changed {}"))
                    .andExpect(status().isNoContent());
            UUID submit = id(read(candidate.post("/candidate/tasks/" + taskId + "/submit").andReturn()
                    .getResponse()), "runId");
            complete(submit, 5, 3, """
                    [{"name":"Скрытый тест 1","status":"PASSED","hidden":true,"key":"g1"},
                     {"name":"Скрытый тест 2","status":"PASSED","hidden":true,"key":"g2"},
                     {"name":"Скрытый тест 3","status":"PASSED","hidden":true,"key":"f1"},
                     {"name":"Скрытый тест 4","status":"FAILED","hidden":true,"key":"f2"},
                     {"name":"Скрытый тест 5","status":"FAILED","hidden":true,"key":"f3"}]
                    """);
            candidate.post("/candidate/session/finish").andExpect(status().isOk());
            for (UUID queued : queuedSubmits(sessionId)) {
                failToCompile(queued);
            }
            assertThat(scoring.compute(sessionId).scored()).isTrue();

            // one of the three tests to fix: 1/3, the two «nothing broken» ones do not add to it
            JsonNode report = read(mvc.perform(get("/employer/sessions/" + sessionId + "/report").session(employer))
                    .andReturn().getResponse());
            JsonNode row = list(report.get("scorePerTask").get("tasks")).stream()
                    .filter(r -> r.get("sessionTaskId").asText().equals(taskId.toString())).findFirst().orElseThrow();
            assertThat(row.get("testsCounted").asInt()).isEqualTo(3);
            assertThat(row.get("guardTests").asInt()).isEqualTo(2);
            assertThat(row.get("share").decimalValue()).isEqualByComparingTo("0.3333");
            JsonNode card = list(report.get("tasks")).stream()
                    .filter(t -> t.get("id").asText().equals(taskId.toString())).findFirst().orElseThrow();
            assertThat(card.get("counted").get("countedPassed").asInt()).isEqualTo(1);
            assertThat(card.get("counted").get("guards").asInt()).isEqualTo(2);
            // the warm-up is in the score (its submit did not compile: 0) and has trust rules in its indicators
            assertThat(list(report.get("scorePerTask").get("tasks")))
                    .anySatisfy(r -> assertThat(r.get("kind").asText()).isEqualTo("CALIBRATION"));
            assertThat(card.get("indicators").get("trustRules").get("value").isArray()).isTrue();
            assertThat(report.toString()).doesNotContain("\"key\"");
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    sessionTasks.findById(taskId).orElseThrow().getVariant()
                            .refreshValidationReport(previous, clock.instant()));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static List<Map<String, Object>> typing(double start, int count, double charsPerSecond) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = start + (i + 1) * 1000 / charsPerSecond;
            events.add(Map.of("t", t, "type", "kd", "keyClass", "letter", "repeat", false));
            events.add(Map.of("t", t, "type", "edit", "file", "src/main/java/A.java", "rangeOffset", i,
                    "rangeLength", 0, "textLength", 1, "text", "a", "source", "typing"));
        }
        return events;
    }

    private void telemetry(Candidate candidate, UUID taskId, int seq, List<Map<String, Object>> events)
            throws Exception {
        double start = ((Number) events.get(0).get("t")).doubleValue();
        double end = ((Number) events.get(events.size() - 1).get("t")).doubleValue();
        mvc.perform(post("/candidate/tasks/" + taskId + "/telemetry").cookie(candidate.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seq", seq, "clientTsStart", start,
                                "clientTsEnd", end, "events", events))))
                .andExpect(status().isOk());
    }

    private List<UUID> queuedSubmits(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                sessionTasks.findBySessionIdOrderByOrderNo(sessionId).stream()
                        .flatMap(t -> runs.findBySessionTaskIdOrderByCreatedAt(t.getId()).stream())
                        .filter(job -> job.getMode() == RunMode.SUBMIT && job.getStatus() == RunStatus.QUEUED)
                        .map(RunJob::getId).toList());
    }

    /** What the runner writes for a submit that does not compile: no test ran. */
    private void failToCompile(UUID runId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RunJob job = runs.findById(runId).orElseThrow();
            job.markRunning("test-runner", clock.instant());
            job.finish(RunStatus.DONE, clock.instant());
            results.save(new RunResult(job, false, "error", 0, 0, "[]", 10L, null, null, clock.instant()));
        });
    }

    private BigDecimal expectedScore(UUID sessionId, UUID taskId, BigDecimal share) {
        Map<Level, BigDecimal> weights = Map.of(Level.JUNIOR, BigDecimal.ONE, Level.MIDDLE, new BigDecimal("1.5"),
                Level.SENIOR, new BigDecimal("2.0"));
        return new TransactionTemplate(transactionManager).execute(status -> {
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal weighted = BigDecimal.ZERO;
            for (var t : sessionTasks.findBySessionIdOrderByOrderNo(sessionId)) {
                // the warm-up counts with 0.5 (here its submit did not compile: 0)
                BigDecimal weight = t.getKind() == ru.gits.core.task.TaskKind.CALIBRATION ? new BigDecimal("0.5")
                        : weights.get(t.getVariant().getLevel());
                total = total.add(weight);
                if (t.getId().equals(taskId)) {
                    weighted = weighted.add(weight.multiply(share));
                }
            }
            return weighted.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
        });
    }
}
