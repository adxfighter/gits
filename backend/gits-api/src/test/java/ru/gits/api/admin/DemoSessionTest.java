package ru.gits.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.api.scoring.ScoringService;
import ru.gits.api.support.CandidateSessionTest;
import ru.gits.api.support.TestAccounts;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.telemetry.TelemetryBatchRepository;
import ru.gits.taskbank.demo.DemoSessionImporter;

/** P16: a finished session is exported as a demo file and seeded into another company, where it is scored again. */
class DemoSessionTest extends CandidateSessionTest {

    @Autowired AppUserRepository users;
    @Autowired ConsentRepository consents;
    @Autowired TelemetryBatchRepository batches;
    @Autowired ScoringService scoring;

    @TempDir
    Path seed;

    @Test
    void exportedSessionIsSeededIntoAnotherCompanyWithItsRecordingAndTheSameScore() throws Exception {
        UUID original = finishedSession();
        MockHttpSession admin = login(accounts.admin());
        String file = mvc.perform(get("/admin/sessions/" + original + "/demo-export")
                        .param("label", "Демо: честное решение").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode demo = json.readTree(file);
        assertThat(demo.get("format").asText()).isEqualTo("gits-demo-session/1");
        assertThat(file).doesNotContain("Иван Петров").doesNotContain("tokenHash").doesNotContain("ipHash");
        Files.writeString(seed.resolve("01-honest.json"), file);

        TestAccounts.Account demoEmployer = accounts.employer();
        DemoSessionImporter importer = importer();
        DemoSessionImporter.Summary summary = importer.importDirectory(seed, demoEmployer.email());
        assertThat(summary.imported()).isEqualTo(1);
        // run again: nothing twice
        assertThat(importer.importDirectory(seed, demoEmployer.email()).present()).isEqualTo(1);

        MockHttpSession employer = login(demoEmployer);
        List<JsonNode> invites = list(read(mvc.perform(get("/employer/invites").session(employer))
                .andReturn().getResponse()));
        assertThat(invites).hasSize(1);
        JsonNode invite = invites.get(0);
        assertThat(invite.get("candidateLabel").asText()).isEqualTo("Демо: честное решение");
        assertThat(invite.get("status").asText()).isEqualTo("COMPLETED");
        UUID seeded = UUID.fromString(invite.get("sessionId").asText());
        // the recording ends an hour before the seed
        assertThat(java.time.Instant.parse(invite.get("finishedAt").asText()))
                .isBefore(clock.instant().minus(Duration.ofMinutes(59)));

        // gits-api scores it as any finished session, with the same result
        ScoringService.Result result = scoring.compute(seeded);
        assertThat(result.scored()).isTrue();
        assertThat(result.preliminaryScore()).isEqualByComparingTo(scoring.compute(original).preliminaryScore());
        JsonNode report = read(mvc.perform(get("/employer/sessions/" + seeded + "/report").session(employer))
                .andExpect(status().isOk()).andReturn().getResponse());
        assertThat(list(report.get("tasks"))).hasSize(4);
        UUID task = UUID.fromString(list(report.get("tasks")).get(1).get("id").asText());
        JsonNode replay = read(mvc.perform(get("/employer/session-tasks/" + task + "/replay").session(employer))
                .andExpect(status().isOk()).andReturn().getResponse());
        assertThat(list(replay.get("events"))).isNotEmpty();
        assertThat(list(replay.get("runs"))).extracting(run -> run.get("mode").asText()).contains("SUBMIT");
    }

    @Test
    void onlyAFinishedSessionWithALabelIsExportedAndOnlyByTheAdmin() throws Exception {
        MockHttpSession admin = login(accounts.admin());
        Candidate candidate = newCandidate();
        UUID running = id(read(candidate.post("/candidate/session/start").andReturn().getResponse()));
        mvc.perform(get("/admin/sessions/" + running + "/demo-export").param("label", "Демо").session(admin))
                .andExpect(status().isConflict());
        // finished, but its submits are still in the queue: the seed would run them again
        Candidate queued = newCandidate();
        UUID unchecked = id(read(queued.post("/candidate/session/start").andReturn().getResponse()));
        queued.post("/candidate/session/finish").andExpect(status().isOk());
        mvc.perform(get("/admin/sessions/" + unchecked + "/demo-export").param("label", "Демо").session(admin))
                .andExpect(status().isConflict());
        UUID finished = finishedSession();
        mvc.perform(get("/admin/sessions/" + finished + "/demo-export").param("label", " ").session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/sessions/" + UUID.randomUUID() + "/demo-export").param("label", "Демо")
                .session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/sessions/" + finished + "/demo-export").param("label", "Демо")
                .session(login(accounts.employer()))).andExpect(status().isForbidden());
    }

    @Test
    void aBadFileIsReportedAndTheOthersAreStillSeeded() throws Exception {
        TestAccounts.Account demoEmployer = accounts.employer();
        Files.writeString(seed.resolve("a-wrong-format.json"), "{\"format\":\"something-else\",\"tasks\":[]}");
        Files.writeString(seed.resolve("b-not-json.json"), "{ not json");
        Files.writeString(seed.resolve("c-queued-run.json"), """
                {"format":"gits-demo-session/1","candidateLabel":"Демо: в очереди","targetLevel":"MIDDLE",
                 "status":"FINISHED","timeLimitMin":90,"randomSeed":1,"startedAt":"2026-09-01T09:00:00Z",
                 "finishedAt":"2026-09-01T10:00:00Z","tasks":[{"orderNo":1,"kind":"TASK","variantCode":"T01-v01",
                 "status":"SUBMITTED","runs":[{"mode":"SUBMIT","status":"QUEUED",
                 "createdAt":"2026-09-01T10:00:00Z","payload":{}}],"telemetry":[]}]}
                """);
        DemoSessionImporter.Summary bad = importer().importDirectory(seed, demoEmployer.email());
        assertThat(bad.files()).isEqualTo(3);
        assertThat(bad.rejected()).isEqualTo(3);
        assertThat(bad.messages()).anySatisfy(m -> assertThat(m).contains("gits-demo-session/1"))
                .anySatisfy(m -> assertThat(m).contains("незавершённый"));
        try (var files = Files.list(seed)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.writeString(seed.resolve("unknown.json"), """
                {"format":"gits-demo-session/1","candidateLabel":"Демо: нет варианта","targetLevel":"MIDDLE",
                 "status":"FINISHED","timeLimitMin":90,"randomSeed":1,"startedAt":"2026-09-01T09:00:00Z",
                 "finishedAt":"2026-09-01T10:00:00Z","tasks":[{"orderNo":1,"kind":"TASK","variantCode":"T99-v09",
                 "status":"SUBMITTED","runs":[],"telemetry":[]}]}
                """);
        DemoSessionImporter.Summary summary = importer().importDirectory(seed, demoEmployer.email());
        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(summary.messages().get(0)).contains("T99-v09");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private DemoSessionImporter importer() {
        return new DemoSessionImporter(new DemoSessionImporter.Repositories(users, invites, consents, sessions,
                sessionTasks, variants, runs, results, batches), transactionManager, clock);
    }

    /** A session where the candidate typed in one task and submitted it, then finished; every submit is checked. */
    private UUID finishedSession() throws Exception {
        Candidate candidate = newCandidate();
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        UUID task = id(list(session.get("tasks")).get(1));
        JsonNode view = read(candidate.get("/candidate/tasks/" + task).andReturn().getResponse());
        String file = view.get("code").fieldNames().next();
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("t", 1000);
        edit.put("type", "edit");
        edit.put("file", file);
        edit.put("rangeOffset", 0);
        edit.put("rangeLength", 0);
        edit.put("textLength", 1);
        edit.put("text", "x");
        edit.put("source", "typing");
        mvc.perform(post("/candidate/tasks/" + task + "/telemetry").cookie(candidate.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seq", 0, "clientTsStart", 0, "clientTsEnd", 1000,
                                "events", List.of(edit)))))
                .andExpect(status().isOk());
        UUID submit = id(read(candidate.post("/candidate/tasks/" + task + "/submit").andReturn().getResponse()),
                "runId");
        complete(submit, 3, 2, hiddenCases());
        clock.advance(Duration.ofMinutes(5));
        candidate.post("/candidate/session/finish").andExpect(status().isOk());
        List<UUID> queued = new TransactionTemplate(transactionManager).execute(status ->
                sessionTasks.findBySessionIdOrderByOrderNo(sessionId).stream()
                        .flatMap(t -> runs.findBySessionTaskIdOrderByCreatedAt(t.getId()).stream())
                        .filter(job -> job.getStatus().name().equals("QUEUED"))
                        .map(job -> job.getId()).toList());
        for (UUID id : queued) {
            complete(id, 2, 0, hiddenCases());
        }
        return sessionId;
    }
}
