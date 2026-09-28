package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunStatus;
import ru.gits.sandbox.SandboxExecutor;

/**
 * End to end through the queue: PostgreSQL (Testcontainers, gits-api migrations) and real sandbox
 * containers. Requires Docker and the gits-sandbox-java:local image.
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=filesystem:../gits-api/src/main/resources/db/migration",
        "gits.runner.concurrency=4",
        "gits.runner.poll-ms=100"
})
@Import(RunnerTestSupport.Postgres.class)
class RunnerIntegrationTest {

    @Autowired private RunnerTestSupport support;
    @Autowired private ObjectMapper json;
    @Autowired private SandboxExecutor sandbox;

    @BeforeAll
    static void requireImage() throws Exception {
        Process inspect = new ProcessBuilder("docker", "image", "inspect", "gits-sandbox-java:local")
                .redirectErrorStream(true).start();
        inspect.getInputStream().readAllBytes();
        assertThat(inspect.waitFor()).as("build the sandbox image: docker build -t gits-sandbox-java:local sandbox/java")
                .isZero();
    }

    @Test
    void runExecutesVisibleTestsOnly() throws Exception {
        UUID task = support.sessionTask();
        UUID job = support.enqueue(task, RunMode.RUN, Map.of(RunnerTestSupport.SUM_PATH, RunnerTestSupport.BUGGY_SUM));

        assertThat(support.awaitFinished(job).getStatus()).isEqualTo(RunStatus.DONE);
        var result = support.result(job);
        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getTestsTotal()).isEqualTo(1);
        assertThat(result.getTestsPassed()).isEqualTo(1);
        assertThat(result.getTestCases()).doesNotContain("secret").doesNotContain("Скрытый");
        assertThat(result.getStdoutTrunc()).contains("emptyArray");
    }

    @Test
    void textFilesAreNeverCompiled() throws Exception {
        UUID task = support.sessionTask();
        // a retyping full of mistakes must not break the Java part of the task
        UUID job = support.enqueue(task, RunMode.RUN, Map.of(RunnerTestSupport.SUM_PATH, RunnerTestSupport.BUGGY_SUM,
                RunnerTestSupport.NOTES_PATH, "public clas Broken {{{ int x = ;"));

        assertThat(support.awaitFinished(job).getStatus()).isEqualTo(RunStatus.DONE);
        var result = support.result(job);
        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getTestsPassed()).isEqualTo(1);
    }

    @Test
    void submitScoresHiddenTestsWithoutRevealingThem() throws Exception {
        UUID task = support.sessionTask();
        UUID buggy = support.enqueue(task, RunMode.SUBMIT, Map.of(RunnerTestSupport.SUM_PATH, RunnerTestSupport.BUGGY_SUM));
        UUID correct = support.enqueue(task, RunMode.SUBMIT, Map.of(RunnerTestSupport.SUM_PATH, RunnerTestSupport.CORRECT_SUM));

        support.awaitFinished(buggy);
        support.awaitFinished(correct);
        var failed = support.result(buggy);
        var passed = support.result(correct);

        assertThat(failed.getTestsTotal()).isEqualTo(3);
        assertThat(failed.getTestsPassed()).isEqualTo(1);  // only the negative-values case survives the bug
        assertThat(passed.getTestsTotal()).isEqualTo(3);
        assertThat(passed.getTestsPassed()).isEqualTo(3);
        for (var result : List.of(failed, passed)) {
            assertThat(result.getStdoutTrunc()).isNull();
            assertThat(result.getTestCases()).doesNotContain("secret").doesNotContain("SumHiddenTest");
            JsonNode cases = json.readTree(result.getTestCases());
            assertThat(cases).filteredOn(c -> c.get("hidden").asBoolean())
                    .extracting(c -> c.get("name").asText())
                    .containsExactlyInAnyOrder("Скрытый тест 1", "Скрытый тест 2", "Скрытый тест 3");
            assertThat(cases).filteredOn(c -> c.get("hidden").asBoolean()).allMatch(c -> c.get("message").isNull());
            // a hidden case keeps a key for scoring (not its name); a visible one has none
            assertThat(cases).filteredOn(c -> c.get("hidden").asBoolean())
                    .allMatch(c -> c.path("key").asText().matches("[0-9a-f]{16}"));
            assertThat(cases).filteredOn(c -> !c.get("hidden").asBoolean()).allMatch(c -> !c.has("key"));
        }
    }

    @Test
    void rejectsFilesTheCandidateMayNotChange() throws Exception {
        UUID task = support.sessionTask();
        UUID job = support.enqueue(task, RunMode.SUBMIT, Map.of(
                "src/test/java/demo/SumHiddenTest.java", "package demo; class SumHiddenTest {}"));

        assertThat(support.awaitFinished(job).getStatus()).isEqualTo(RunStatus.ERROR);
        assertThat(support.result(job).getCompileOutput()).contains("нельзя изменять");
    }

    @Test
    void blocksForbiddenApis() throws Exception {
        UUID task = support.sessionTask();
        String exiting = RunnerTestSupport.CORRECT_SUM.replace("int total = 0;", "System.exit(0); int total = 0;");
        UUID job = support.enqueue(task, RunMode.SUBMIT, Map.of(RunnerTestSupport.SUM_PATH, exiting));

        assertThat(support.awaitFinished(job).getStatus()).isEqualTo(RunStatus.DONE);
        var result = support.result(job);
        assertThat(result.isCompiled()).isFalse();
        assertThat(result.getCompileOutput()).contains("System.exit");
        assertThat(result.getTestsPassed()).isZero();
    }

    @Test
    void submitCompileErrorsDoNotRevealHiddenTestCode() throws Exception {
        UUID task = support.sessionTask();
        // Renaming the method breaks the hidden test compilation, whose diagnostics would show its code
        String renamed = RunnerTestSupport.CORRECT_SUM.replace("int of(", "int total(");
        UUID job = support.enqueue(task, RunMode.SUBMIT, Map.of(RunnerTestSupport.SUM_PATH, renamed));

        support.awaitFinished(job);
        var result = support.result(job);
        assertThat(result.isCompiled()).isFalse();
        assertThat(result.getCompileOutput()).doesNotContain("SumHiddenTest").doesNotContain("secret");
    }

    @Test
    void processesManyJobsInParallelAndCleansUp() throws Exception {
        UUID task = support.sessionTask();
        List<UUID> jobIds = IntStream.range(0, 10)
                .mapToObj(i -> support.enqueue(task, RunMode.RUN, Map.of(RunnerTestSupport.SUM_PATH, RunnerTestSupport.CORRECT_SUM)))
                .toList();

        for (UUID id : jobIds) {
            assertThat(support.awaitFinished(id).getStatus()).isEqualTo(RunStatus.DONE);
            assertThat(support.result(id).getTestsPassed()).isEqualTo(1);
        }
        assertThat(sandbox.removeOrphans()).as("no sandbox containers left behind").isZero();
    }
}
