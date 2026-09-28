package ru.gits.api.employer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.api.security.Hashing;
import ru.gits.api.support.CandidateSessionTest;
import ru.gits.core.invite.InviteStatus;
import ru.gits.core.result.SessionIndicators;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.result.TrustLevel;

/** P09 acceptance: invites with results, report, replay, revoking, isolation of companies, no secrets. */
class EmployerApiTest extends CandidateSessionTest {

    @Autowired SessionScoreRepository scores;
    @Autowired SessionIndicatorsRepository indicatorRows;

    @Test
    void inviteListShowsSessionScoreAndWorstTrustLevel() throws Exception {
        Finished done = finishedSession();

        JsonNode row = rowOf(read(mvc.perform(get("/employer/invites").session(done.employer()))
                .andExpect(status().isOk()).andReturn().getResponse()), done.inviteId());
        assertThat(row.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(row.get("sessionId").asText()).isEqualTo(done.sessionId().toString());
        assertThat(row.get("sessionStatus").asText()).isEqualTo("FINISHED");
        assertThat(row.get("preliminaryScore").decimalValue()).isEqualByComparingTo("72.50");
        assertThat(row.get("trustLevel").asText()).isEqualTo("YELLOW");

        assertThat(rowOf(read(mvc.perform(get("/employer/invites?status=COMPLETED").session(done.employer()))
                .andReturn().getResponse()), done.inviteId())).isNotNull();
        assertThat(rowOf(read(mvc.perform(get("/employer/invites?status=CREATED").session(done.employer()))
                .andReturn().getResponse()), done.inviteId())).isNull();
    }

    @Test
    void reportHasResultsPerTaskAndNoSecrets() throws Exception {
        Finished done = finishedSession();

        String body = mvc.perform(get("/employer/sessions/" + done.sessionId() + "/report").session(done.employer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode report = json.readTree(body);

        assertThat(report.get("preliminaryScore").decimalValue()).isEqualByComparingTo("72.50");
        assertThat(report.get("trustLevel").asText()).isEqualTo("YELLOW");
        assertThat(report.get("candidateLabel").asText()).isNotBlank();
        List<JsonNode> tasks = list(report.get("tasks"));
        assertThat(tasks).hasSize(4);
        JsonNode task = tasks.stream().filter(t -> t.get("id").asText().equals(done.taskId().toString()))
                .findFirst().orElseThrow();
        assertThat(task.get("templateCode").asText()).startsWith("T0");
        assertThat(list(task.get("competencies"))).isNotEmpty();
        assertThat(task.get("level").asText()).isNotBlank();
        // hiddenCases(): two hidden tests, one passed
        assertThat(task.get("hiddenTestsPassed").asInt()).isEqualTo(1);
        assertThat(task.get("hiddenTestsTotal").asInt()).isEqualTo(2);
        assertThat(task.get("runs").asInt()).isEqualTo(1);
        assertThat(task.get("durationSeconds").isNumber()).isTrue();
        assertThat(task.get("trustLevel").asText()).isEqualTo("YELLOW");
        assertThat(task.get("indicators").get("pasteRatio").get("explanation").asText()).contains("вставк");
        assertThat(task.get("finalCode").toString()).contains("// final answer");

        for (JsonNode each : tasks) {
            UUID variantId = sessionTasks.findById(UUID.fromString(each.get("id").asText())).orElseThrow()
                    .getVariant().getId();
            assertNoSecrets(body, variantId);
        }
    }

    @Test
    void replayGivesStartingFilesRunsAndReplayEventsPageBySeq() throws Exception {
        Finished done = finishedSession();
        String path = "/employer/session-tasks/" + done.taskId() + "/replay";

        String body = mvc.perform(get(path).session(done.employer())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        JsonNode replay = json.readTree(body);
        assertThat(list(replay.get("initialFiles"))).isNotEmpty()
                .allSatisfy(file -> assertThat(file.get("kind").asText()).isIn("STARTER", "READONLY", "VISIBLE_TEST"));
        assertThat(list(replay.get("runs"))).extracting(run -> run.get("mode").asText())
                .containsExactly("RUN", "SUBMIT");
        List<JsonNode> events = list(replay.get("events"));
        // key and cursor events are not part of the replay
        assertThat(events).extracting(event -> event.get("type").asText())
                .containsExactly("edit", "run", "paste", "edit", "submit");
        assertThat(events.get(0).get("seq").asInt()).isZero();
        assertThat(events.get(0).get("text").asText()).isEqualTo("x");
        assertThat(replay.get("nextSeq").isNull()).isTrue();
        assertNoSecrets(body, sessionTasks.findById(done.taskId()).orElseThrow().getVariant().getId());

        JsonNode first = read(mvc.perform(get(path + "?limit=1").session(done.employer())).andReturn().getResponse());
        assertThat(list(first.get("events"))).extracting(event -> event.get("seq").asInt()).containsOnly(0);
        assertThat(first.get("nextSeq").asInt()).isEqualTo(1);
        JsonNode second = read(mvc.perform(get(path + "?fromSeq=1&limit=1").session(done.employer())).andReturn()
                .getResponse());
        assertThat(list(second.get("events"))).extracting(event -> event.get("seq").asInt()).containsOnly(1);
        assertThat(second.get("nextSeq").isNull()).isTrue();
    }

    @Test
    void anotherCompanySeesNothing() throws Exception {
        Finished done = finishedSession();
        MockHttpSession other = login(accounts.employer());

        assertThat(rowOf(read(mvc.perform(get("/employer/invites").session(other)).andExpect(status().isOk())
                .andReturn().getResponse()), done.inviteId())).isNull();
        mvc.perform(get("/employer/sessions/" + done.sessionId() + "/report").session(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/employer/session-tasks/" + done.taskId() + "/replay").session(other))
                .andExpect(status().isNotFound());
        String token = createInviteToken(done.employer());
        UUID unused = invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().getId();
        mvc.perform(delete("/employer/invites/" + unused).session(other).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(invites.findById(unused).orElseThrow().getStatus()).isEqualTo(InviteStatus.CREATED);
    }

    @Test
    void onlyAnUnusedInviteCanBeRevoked() throws Exception {
        Finished done = finishedSession();
        String token = createInviteToken(done.employer());
        UUID unused = invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().getId();

        mvc.perform(delete("/employer/invites/" + unused).session(done.employer()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(invites.findById(unused).orElseThrow().getStatus()).isEqualTo(InviteStatus.REVOKED);
        mvc.perform(post("/candidate/enter").with(csrf()).with(client()).contentType(MediaType.APPLICATION_JSON)
                .content(body("token", token))).andExpect(status().isGone());
        // repeating is harmless
        mvc.perform(delete("/employer/invites/" + unused).session(done.employer()).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/employer/invites/" + done.inviteId()).session(done.employer()).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(invites.findById(done.inviteId()).orElseThrow().getStatus()).isEqualTo(InviteStatus.COMPLETED);
    }

    @Test
    void candidateCannotUseTheEmployerApi() throws Exception {
        Candidate candidate = newCandidate();
        mvc.perform(get("/employer/invites").cookie(candidate.cookie())).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Finished(MockHttpSession employer, UUID inviteId, UUID sessionId, UUID taskId) {
    }

    /**
     * A finished session of a new company: one task with telemetry, a run and a submit, indicators of two tasks and
     * a preliminary score written the way P12 will write them.
     */
    private Finished finishedSession() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        Candidate candidate = newCandidate(employer);
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        List<JsonNode> tasks = list(session.get("tasks"));
        UUID taskId = id(tasks.get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andReturn().getResponse());
        String file = task.get("code").fieldNames().next();

        telemetry(candidate, taskId, 0, 0, 1000, List.of(
                Map.of("t", 10, "type", "kd", "keyClass", "letter", "repeat", false),
                Map.of("t", 11, "type", "edit", "file", file, "rangeOffset", 0, "rangeLength", 0, "textLength", 1,
                        "text", "x", "source", "typing"),
                Map.of("t", 12, "type", "cursor", "file", file, "offset", 1),
                Map.of("t", 500, "type", "run")));
        telemetry(candidate, taskId, 1, 1000, 2000, List.of(
                Map.of("t", 1100, "type", "paste", "file", file, "length", 15),
                Map.of("t", 1101, "type", "edit", "file", file, "rangeOffset", 1, "rangeLength", 0, "textLength", 15,
                        "text", "// final answer", "source", "paste"),
                Map.of("t", 1900, "type", "submit")));

        candidate.put("/candidate/tasks/" + taskId + "/code", Map.of(file, "// final answer\n"))
                .andExpect(status().isNoContent());
        UUID runId = id(read(candidate.post("/candidate/tasks/" + taskId + "/run").andReturn().getResponse()),
                "runId");
        complete(runId, 2, 1, visibleCases());
        clock.advance(java.time.Duration.ofMinutes(3));
        UUID submitId = id(read(candidate.post("/candidate/tasks/" + taskId + "/submit").andReturn().getResponse()),
                "runId");
        complete(submitId, 3, 2, hiddenCases());
        candidate.post("/candidate/session/finish").andExpect(status().isOk());

        UUID calibrationId = id(tasks.get(0));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            indicatorRows.save(new SessionIndicators(sessionTasks.getReferenceById(taskId), """
                    {"pasteRatio": {"value": 0.62, "explanation": "Большая доля кода добавлена вставкой"}}""",
                    TrustLevel.YELLOW, clock.instant()));
            indicatorRows.save(new SessionIndicators(sessionTasks.getReferenceById(calibrationId), "{}",
                    TrustLevel.GREEN, clock.instant()));
            scores.save(new SessionScore(sessions.getReferenceById(sessionId), "{}", new BigDecimal("72.50"),
                    clock.instant()));
        });
        UUID inviteId = new TransactionTemplate(transactionManager).execute(status ->
                sessions.findById(sessionId).orElseThrow().getInvite().getId());
        return new Finished(employer, inviteId, sessionId, taskId);
    }

    private void telemetry(Candidate candidate, UUID taskId, int seq, double start, double end,
                           List<Map<String, Object>> events) throws Exception {
        mvc.perform(post("/candidate/tasks/" + taskId + "/telemetry").cookie(candidate.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seq", seq, "clientTsStart", start,
                                "clientTsEnd", end, "events", events))))
                .andExpect(status().isOk());
    }

    private static JsonNode rowOf(JsonNode rows, UUID inviteId) {
        for (JsonNode row : rows) {
            if (row.get("id").asText().equals(inviteId.toString())) {
                return row;
            }
        }
        return null;
    }
}
