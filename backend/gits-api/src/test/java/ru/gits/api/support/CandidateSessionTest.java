package ru.gits.api.support;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;

import ru.gits.api.session.SessionService;
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
 * Base of tests of the candidate session API: a bank built from copies of T00-example at the needed levels plus the
 * calibration block (loaded once per database), a clock moved by hand, candidates who entered and accepted consent,
 * and a stand-in for the runner that finishes jobs the way the runner writes run_result.
 */
@Import(CandidateSessionTest.ClockConfig.class)
public abstract class CandidateSessionTest extends ApiTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    protected static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    /** Clock the test moves by hand; replaces the system clock for the whole context of this class. */
    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);

        public void set(Instant instant) {
            now.set(instant);
        }

        public void advance(Duration duration) {
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
    public static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired protected MutableClock clock;
    @Autowired protected SessionService sessionService;
    @Autowired protected TaskTemplateRepository templates;
    @Autowired protected TaskVariantRepository variants;
    @Autowired protected TaskFileRepository taskFiles;
    @Autowired protected SessionTaskRepository sessionTasks;
    @Autowired protected AssessmentSessionRepository sessions;
    @Autowired protected RunJobRepository runs;
    @Autowired protected RunResultRepository results;
    @Autowired protected InviteRepository invites;
    @Autowired protected PlatformTransactionManager transactionManager;

    @BeforeEach
    void loadBankOnce() throws IOException {
        clock.set(START);
        if (variants.findByCode("T01-v01").isPresent()) {
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
    }


    /** Candidate who entered through an invite link and accepted consent. */
    protected Candidate newCandidate() throws Exception {
        return newCandidate(login(accounts.employer()));
    }

    /** Candidate invited by the given employer, i.e. of the employer's company. */
    protected Candidate newCandidate(MockHttpSession employer) throws Exception {
        String token = createInviteToken(employer);
        Cookie cookie = candidateCookie(mvc.perform(post("/candidate/enter").with(csrf()).with(client())
                .contentType(MediaType.APPLICATION_JSON).content(body("token", token))).andReturn().getResponse());
        MockHttpServletResponse consent = mvc.perform(post("/candidate/consent").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        return new Candidate(candidateCookie(consent));
    }

    /** A candidate whose cookie was obtained by the test itself (for example, with its own request headers). */
    protected Candidate candidate(Cookie cookie) {
        return new Candidate(cookie);
    }

    protected final class Candidate {
        private final Cookie cookie;

        Candidate(Cookie cookie) {
            this.cookie = cookie;
        }

        public Cookie cookie() {
            return cookie;
        }

        public ResultActions get(String path) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .cookie(cookie));
        }

        public ResultActions post(String path) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .cookie(cookie).with(csrf()));
        }

        public ResultActions put(String path, Map<String, String> files) throws Exception {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                    .cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("files", files))));
        }
    }

    /**
     * The response contains nothing of the variant's SOLUTION and HIDDEN_TEST files, nor its validation data. Only
     * lines found exclusively in the secret files are checked: shared lines (package, imports) prove nothing.
     */
    protected void assertNoSecrets(String response, UUID variantId) throws Exception {
        List<TaskFile> secret = taskFiles.findByVariantIdAndKindIn(variantId,
                List.of(FileKind.SOLUTION, FileKind.HIDDEN_TEST));
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

    protected Set<String> variantCodes(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                sessionTasks.findBySessionIdOrderByOrderNo(sessionId).stream()
                        .map(task -> task.getVariant().getCode()).collect(Collectors.toSet()));
    }

    /** Sends the same request from two threads at once. */
    protected static List<MockHttpServletResponse> concurrently(Callable<MockHttpServletResponse> request)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return request.call();
                }));
            }
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    protected InviteStatus inviteStatus(UUID sessionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                sessions.findById(sessionId).orElseThrow().getInvite().getStatus());
    }

    /** Does what the runner does when a job is finished. */
    protected void complete(UUID runId, int total, int passed, String cases) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RunJob job = runs.findById(runId).orElseThrow();
            job.markRunning("test-runner", clock.instant());
            job.finish(RunStatus.DONE, clock.instant());
            results.save(new RunResult(job, true, null, total, passed, cases, 120L, "secret-stdout", null,
                    clock.instant()));
        });
    }

    protected static String visibleCases() {
        return """
                [{"name":"GradeStatisticsVisibleTest › passes","status":"PASSED","message":null,"hidden":false},
                 {"name":"GradeStatisticsVisibleTest › fails","status":"FAILED","message":"expected: <1>","hidden":false}]
                """;
    }

    protected static String hiddenCases() {
        return """
                [{"name":"GradeStatisticsVisibleTest › passes","status":"PASSED","message":null,"hidden":false},
                 {"name":"Скрытый тест 1","status":"PASSED","message":null,"hidden":true},
                 {"name":"Скрытый тест 2","status":"FAILED","message":"expected: <5>","hidden":true}]
                """;
    }

    protected static List<JsonNode> list(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    protected static UUID id(JsonNode node) {
        return id(node, "id");
    }

    protected static UUID id(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asText());
    }

    /** Copy of T00-example registered as template {@code code} with the given level, re-hashed like validate. */
    protected static void copyExample(Path tasks, String code, String level) throws IOException {
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

    protected static void copyTree(Path source, Path target) throws IOException {
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
