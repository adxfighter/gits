package ru.gits.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs real containers. Requires Docker and the sandbox image (scripts/up.* or
 * {@code docker build -t gits-sandbox-java:local sandbox/java}).
 */
class SandboxExecutorDockerTest {

    static final String IMAGE = System.getProperty("gits.sandbox.image", "gits-sandbox-java:local");

    private static final SourceFile CALC = new SourceFile("src/main/java/demo/Calc.java", """
            package demo;
            public class Calc { public static int twice(int x) { return x * 2; } }
            """);
    private static final SourceFile CALC_TEST = new SourceFile("src/test/java/demo/CalcTest.java", """
            package demo;
            import static org.assertj.core.api.Assertions.assertThat;
            import org.junit.jupiter.api.Test;
            class CalcTest {
                @Test void twiceOfTwo() { assertThat(Calc.twice(2)).isEqualTo(4); }
                @Test void twiceOfZero() { assertThat(Calc.twice(0)).isEqualTo(0); }
                @Test void wrongExpectation() { assertThat(Calc.twice(3)).isEqualTo(7); }
            }
            """);

    private final SandboxExecutor executor = new SandboxExecutor(SandboxConfig.defaults(IMAGE, "runc"));

    @BeforeAll
    static void requireImage() throws Exception {
        Process inspect = new ProcessBuilder("docker", "image", "inspect", IMAGE).redirectErrorStream(true).start();
        inspect.getInputStream().readAllBytes();
        assertThat(inspect.waitFor())
                .as("Sandbox image %s is missing: docker build -t %s sandbox/java", IMAGE, IMAGE)
                .isZero();
    }

    @Test
    void runsTestsAndReportsEachCase() {
        SandboxRun run = executor.run(SourceArchive.build(List.of(CALC, CALC_TEST)));
        ParsedRun parsed = SandboxOutputParser.parse(run);

        assertThat(run.timedOut()).isFalse();
        assertThat(parsed.compiled()).isTrue();
        assertThat(parsed.testCases()).hasSize(3);
        assertThat(parsed.testCases()).filteredOn(TestCaseResult::passed).hasSize(2);
        assertThat(parsed.testCases()).filteredOn(c -> !c.passed()).singleElement()
                .satisfies(c -> assertThat(c.message()).contains("7"));
        assertThat(ExpectedTests.of(List.of(CALC_TEST)).matches(parsed.testCases())).isTrue();
    }

    @Test
    void reportsCompilationErrors() {
        var broken = new SourceFile("src/main/java/demo/Calc.java", "package demo; public class Calc { int x = ; }");

        ParsedRun parsed = SandboxOutputParser.parse(executor.run(SourceArchive.build(List.of(broken, CALC_TEST))));

        assertThat(parsed.compiled()).isFalse();
        assertThat(parsed.compileOutput()).contains("Calc.java");
    }

    @Test
    void killsRunsThatExceedTheTimeout() {
        var shortTimeout = new SandboxExecutor(new SandboxConfig("docker", IMAGE, "runc", Duration.ofSeconds(8), 1 << 20));
        var loop = new SourceFile("src/test/java/demo/LoopTest.java", """
                package demo;
                import org.junit.jupiter.api.Test;
                class LoopTest { @Test void spin() { while (true) { Thread.onSpinWait(); } } }
                """);

        SandboxRun run = shortTimeout.run(SourceArchive.build(List.of(loop)));

        assertThat(run.timedOut()).isTrue();
        assertThat(run.durationMs()).isLessThan(25_000);
        assertThat(executor.removeOrphans()).isZero();
    }

    @Test
    void parallelRunsLeaveNoContainersBehind() {
        byte[] archive = SourceArchive.build(List.of(CALC, CALC_TEST));

        List<SandboxRun> runs = IntStream.range(0, 4)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> executor.run(archive)))
                .toList().stream().map(CompletableFuture::join).toList();

        assertThat(runs).allSatisfy(run -> assertThat(SandboxOutputParser.parse(run).testCases()).hasSize(3));
        assertThat(executor.removeOrphans()).isZero();
    }
}
