package ru.gits.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;
import ru.gits.api.support.CandidateSessionTest;
import ru.gits.api.support.TestAccounts;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.invite.Consent;
import ru.gits.core.invite.ConsentRepository;

/** P15: the administrator's task bank and sessions, and the research export without personal data. */
class AdminApiTest extends CandidateSessionTest {

    private static final String USER_AGENT = "GitsTestBrowser/7.3 (secret-device)";
    private static final String IP = "203.0.113.77";

    @Autowired ConsentRepository consents;
    @Autowired AuditLogRepository audit;

    @Test
    void onlyAnAdministratorOpensTheAdminSection() throws Exception {
        MockHttpSession employer = login(accounts.employer());
        for (String path : List.of("/admin/tasks", "/admin/sessions", "/admin/export",
                "/admin/tasks/variants/" + UUID.randomUUID())) {
            mvc.perform(get(path).session(employer)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/admin/sessions/" + UUID.randomUUID() + "/scoring").session(employer).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void taskBankShowsVariantsWithValidationAndIssuesAndEveryFileToTheAdmin() throws Exception {
        Finished finished = finishedSession(DAY);
        MockHttpSession admin = login(accounts.admin());

        List<JsonNode> templates = list(read(mvc.perform(get("/admin/tasks").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse()));
        assertThat(templates).extracting(t -> t.get("code").asText()).contains("CAL", "T01", "T02");
        List<JsonNode> variantRows = templates.stream().flatMap(t -> list(t.get("variants")).stream()).toList();
        assertThat(variantRows).allSatisfy(row -> {
            assertThat(row.get("status").asText()).isIn("VALIDATED", "DISABLED");
            assertThat(row.has("validationPassed")).isTrue();
        });
        // a task of the session (the warm-up has no hidden tests)
        JsonNode issued = variantRows.stream()
                .filter(row -> finished.variantCodes().contains(row.get("code").asText()))
                .filter(row -> row.get("kind").asText().equals("TASK"))
                .findFirst().orElseThrow();
        assertThat(issued.get("issued").asLong()).isPositive();

        JsonNode variant = read(mvc.perform(get("/admin/tasks/variants/" + issued.get("id").asText()).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse());
        assertThat(variant.get("statementMd").asText()).isNotBlank();
        assertThat(list(variant.get("files"))).extracting(file -> file.get("kind").asText())
                .contains("STARTER", "VISIBLE_TEST", "SOLUTION", "HIDDEN_TEST");
        mvc.perform(get("/admin/tasks/variants/" + UUID.randomUUID()).session(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessionsOfEveryCompanyAreListedAndCanBeRescored() throws Exception {
        Finished finished = finishedSession(DAY.plus(Duration.ofDays(1)));
        MockHttpSession admin = login(accounts.admin());

        List<JsonNode> rows = list(read(mvc.perform(get("/admin/sessions").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse()));
        JsonNode row = rows.stream().filter(r -> r.get("sessionId").asText().equals(finished.sessionId().toString()))
                .findFirst().orElseThrow();
        assertThat(row.get("companyName").asText()).startsWith("Компания ");
        assertThat(row.get("status").asText()).isEqualTo("FINISHED");
        assertThat(row.get("preliminaryScore").isNull()).isFalse();

        mvc.perform(post("/admin/sessions/" + finished.sessionId() + "/scoring").session(admin).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void exportIsPseudonymisedAndHasNoLabelEmailIpOrUserAgent() throws Exception {
        Finished finished = finishedSession(DAY.plus(Duration.ofDays(2)));
        TestAccounts.Account adminAccount = accounts.admin();
        MockHttpSession admin = login(adminAccount);

        Map<String, String> archive = export(admin, "?from=2031-05-12&to=2031-05-12");
        assertThat(archive.keySet()).containsExactlyElementsOf(ExportService.FILES);
        assertThat(archive.get("README.md")).contains("Псевдонимизация", "sessions.jsonl", "telemetry.jsonl");

        List<JsonNode> sessionLines = lines(archive.get("sessions.jsonl"));
        assertThat(sessionLines).hasSize(1);
        JsonNode session = sessionLines.get(0);
        String pseudonym = session.get("session").asText();
        assertThat(pseudonym).matches("s-[0-9a-f]{16}");
        assertThat(session.get("company").asText()).matches("c-[0-9a-f]{16}");
        assertThat(session.get("consentVersion").asInt()).isPositive();
        assertThat(session.get("preliminaryScore").isNull()).isFalse();
        List<JsonNode> tasks = lines(archive.get("tasks.jsonl"));
        assertThat(tasks).hasSize(4).allSatisfy(task -> {
            assertThat(task.get("session").asText()).isEqualTo(pseudonym);
            assertThat(task.get("task").asText()).matches("t-[0-9a-f]{16}");
        });
        // the per-task score points at the archive's task pseudonyms
        List<String> taskPseudonyms = tasks.stream().map(task -> task.get("task").asText()).toList();
        assertThat(list(session.get("scorePerTask").get("tasks"))).isNotEmpty()
                .allSatisfy(t -> assertThat(taskPseudonyms).contains(t.get("task").asText()));
        assertThat(lines(archive.get("telemetry.jsonl"))).isNotEmpty()
                .allSatisfy(batch -> assertThat(taskPseudonyms).contains(batch.get("task").asText()));
        assertThat(lines(archive.get("runs.jsonl"))).isNotEmpty();
        assertThat(lines(archive.get("indicators.jsonl"))).hasSize(4);
        assertThat(read(archive.get("manifest.json")).get("counts").get("sessions").asInt()).isEqualTo(1);

        // nothing that names the candidate, the employer, the company or the device, and no database identifiers
        String everything = String.join("\n", archive.values());
        Consent consent = consent(finished.inviteId());
        // the consent did store the address (hashed) and the browser: they are there to be left out
        assertThat(consent.getIpHash()).isNotBlank();
        assertThat(consent.getUserAgent()).isEqualTo(USER_AGENT);
        List<String> forbidden = new ArrayList<>(List.of("Иван Петров", finished.employer().email(),
                adminAccount.email(), finished.companyName(), USER_AGENT, "secret-device", IP,
                consent.getIpHash(), finished.sessionId().toString(), finished.inviteId().toString()));
        finished.taskIds().forEach(id -> forbidden.add(id.toString()));
        assertThat(forbidden).allSatisfy(value -> assertThat(everything).doesNotContain(value));
        assertThat(everything).doesNotContainIgnoringCase("candidateLabel").doesNotContain("@test.local")
                .doesNotContain("userAgent").doesNotContain("ipHash");

        // a new archive gets new pseudonyms: two exports cannot be joined
        Map<String, String> again = export(admin, "?from=2031-05-12&to=2031-05-12");
        assertThat(lines(again.get("sessions.jsonl")).get(0).get("session").asText()).isNotEqualTo(pseudonym);
        // the export is recorded
        assertThat(audit.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("RESEARCH_EXPORT");
            assertThat(entry.getActor()).isEqualTo(adminAccount.email());
        });
    }

    @Test
    void exportTakesOnlySessionsStartedInThePeriod() throws Exception {
        finishedSession(DAY.plus(Duration.ofDays(3)));
        MockHttpSession admin = login(accounts.admin());

        // the session started on 2031-05-13 at 10:00 UTC; nothing else in the database is that late
        assertThat(lines(export(admin, "?from=2031-05-14").get("sessions.jsonl"))).isEmpty();
        assertThat(lines(export(admin, "?from=2031-05-13").get("sessions.jsonl"))).hasSize(1);
        assertThat(lines(export(admin, "?from=2031-05-13&to=2031-05-13").get("sessions.jsonl"))).hasSize(1);
        mvc.perform(get("/admin/export?from=2031-05-14&to=2031-05-13").session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/export?to=+999999999-12-31").session(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/export?from=1970-01-01").session(admin)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Finished(TestAccounts.Account employer, String companyName, UUID inviteId, UUID sessionId,
                            List<UUID> taskIds, List<String> variantCodes) {
    }

    /** Every test starts its session on a day of its own: the database is shared with the other tests. */
    private static final Instant DAY = Instant.parse("2031-05-10T10:00:00Z");

    /**
     * A session of its own company started at {@code start}: the candidate types in a task, submits it and finishes;
     * every submit is checked, so the session is scored.
     */
    private Finished finishedSession(Instant start) throws Exception {
        clock.set(start);
        TestAccounts.Account employerAccount = accounts.employer();
        MockHttpSession employer = login(employerAccount);
        Cookie cookie = enterWithUserAgent(employer);
        Candidate candidate = candidate(cookie);
        JsonNode session = read(candidate.post("/candidate/session/start").andReturn().getResponse());
        UUID sessionId = id(session);
        List<UUID> taskIds = list(session.get("tasks")).stream().map(CandidateSessionTest::id).toList();
        UUID task = taskIds.get(1);
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
        mvc.perform(post("/candidate/tasks/" + task + "/telemetry").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seq", 0, "clientTsStart", 0, "clientTsEnd", 1000,
                                "events", List.of(edit)))))
                .andExpect(status().isOk());
        UUID submit = id(read(candidate.post("/candidate/tasks/" + task + "/submit").andReturn().getResponse()),
                "runId");
        complete(submit, 3, 2, hiddenCases());
        clock.advance(Duration.ofMinutes(5));
        candidate.post("/candidate/session/finish").andExpect(status().isOk());
        // the automatic submits of the other tasks are checked too: then the session is scored
        for (UUID queued : queuedSubmits(sessionId)) {
            complete(queued, 2, 0, hiddenCases());
        }
        mvc.perform(post("/admin/sessions/" + sessionId + "/scoring").session(login(accounts.admin())).with(csrf()))
                .andExpect(status().isOk());
        var invite = new TransactionTemplate(transactionManager).execute(status -> {
            var s = sessions.findById(sessionId).orElseThrow();
            return List.of(s.getInvite().getId().toString(), s.getInvite().getCompany().getName());
        });
        return new Finished(employerAccount, invite.get(1), UUID.fromString(invite.get(0)), sessionId, taskIds,
                variantCodes(sessionId).stream().sorted().toList());
    }

    /** The candidate enters from a browser with a recognisable user agent: it must not reach the export. */
    private Cookie enterWithUserAgent(MockHttpSession employer) throws Exception {
        String token = createInviteToken(employer);
        Cookie cookie = candidateCookie(mvc.perform(post("/candidate/enter").with(csrf()).with(knownAddress())
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON).content(body("token", token)))
                .andReturn().getResponse());
        MockHttpServletResponse consent = mvc.perform(post("/candidate/consent").cookie(cookie).with(csrf())
                        .with(knownAddress()).header("User-Agent", USER_AGENT))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        return candidateCookie(consent);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor knownAddress() {
        return request -> {
            request.setRemoteAddr(IP);
            return request;
        };
    }

    private Consent consent(UUID inviteId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                consents.findByInviteIdIn(List.of(inviteId)).get(0));
    }

    private List<UUID> queuedSubmits(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                sessionTasks.findBySessionIdOrderByOrderNo(sessionId).stream()
                        .flatMap(t -> runs.findBySessionTaskIdOrderByCreatedAt(t.getId()).stream())
                        .filter(job -> job.getStatus().name().equals("QUEUED"))
                        .map(job -> job.getId()).toList());
    }

    private Map<String, String> export(MockHttpSession admin, String query) throws Exception {
        MvcResult started = mvc.perform(get("/admin/export" + query).session(admin))
                .andExpect(request().asyncStarted()).andReturn();
        MockHttpServletResponse response = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains(".zip");
        return unzip(response.getContentAsByteArray());
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private List<JsonNode> lines(String jsonl) throws IOException {
        List<JsonNode> result = new ArrayList<>();
        for (String line : jsonl.split("\n")) {
            if (!line.isBlank()) {
                result.add(json.readTree(line));
            }
        }
        return result;
    }

    private JsonNode read(String text) throws IOException {
        return json.readTree(text);
    }
}
