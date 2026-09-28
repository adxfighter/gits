package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates copies of tasks/java/T00-example and of a calibration block in real sandbox containers (needs Docker and
 * the image). Checks that fail before the sandbox are covered without Docker in {@link CalibrationRulesTest}.
 */
class TaskBankValidatorDockerTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final String VARIANT = "java/T00-example/variants/v01";
    private static final String CALIBRATION_TEMPLATE = "java/CAL-calibration";
    private static final String CALIBRATION_VARIANT = CALIBRATION_TEMPLATE + "/variants/v01";

    @TempDir
    Path temp;

    @BeforeAll
    static void requireImage() throws Exception {
        Process inspect = new ProcessBuilder("docker", "image", "inspect", "gits-sandbox-java:local")
                .redirectErrorStream(true).start();
        inspect.getInputStream().readAllBytes();
        assertThat(inspect.waitFor()).as("build the sandbox image: docker build -t gits-sandbox-java:local sandbox/java")
                .isZero();
    }

    @Test
    void exampleTaskPassesAndHashIsVerifiable() throws Exception {
        Path tasks = copyBank();

        Result validate = cli("validate", tasks.resolve("java").toString());

        assertThat(validate.code()).as(validate.output()).isEqualTo(TaskBankCli.EXIT_OK);
        assertThat(validate.output()).contains("PASSED T00-v01");
        ValidationReport report = ReportFiles.read(tasks.resolve(VARIANT)).orElseThrow();
        assertThat(report.status()).isEqualTo(ValidationReport.Status.PASSED);
        assertThat(report.checks()).extracting(ValidationReport.Check::id).containsExactly(
                "schema", "files", "forbidden", "sizes", "no_leak",
                "starter_compiles", "reference_passes", "starter_fails", "reference_time", "content_hash");
        assertThat(report.runs().referencePassed()).isEqualTo(3);
        assertThat(report.runs().starterFailed()).isEqualTo(3);
        // the hidden cases the starter already passes check that nothing got broken; parameterised rows count one by one
        assertThat(report.runs().starterPassingHidden()).hasSize(3).allSatisfy(key -> assertThat(key).matches("[0-9a-f]{16}"));
        assertThat(report.checks()).filteredOn(check -> check.id().equals("starter_fails")).singleElement()
                .satisfies(check -> assertThat(check.details()).contains("исправить нужно 4 из 7 скрытых проверок"));
        assertThat(cli("verify-hashes", tasks.resolve("java").toString()).code()).isEqualTo(TaskBankCli.EXIT_OK);

        // Any edit after validation invalidates the stored report
        Path statement = tasks.resolve(VARIANT).resolve("statement.md");
        Files.writeString(statement, Files.readString(statement) + "\nДополнение.");
        Result verify = cli("verify-hashes", tasks.resolve("java").toString());
        assertThat(verify.code()).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(verify.output()).contains("content_hash не совпадает");
    }

    @Test
    void solutionThatDoesNotFixTheBugFails() throws Exception {
        Path tasks = copyBank();
        Path variant = tasks.resolve(VARIANT);
        String file = "src/main/java/ru/gits/task/education/t00/GradeStatistics.java";
        Files.copy(variant.resolve("starter").resolve(file), variant.resolve("solution").resolve(file),
                StandardCopyOption.REPLACE_EXISTING);

        Result validate = cli("validate", tasks.resolve("java").toString(), "--runs", "1");

        assertThat(validate.code()).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(validate.output()).contains("FAILED T00-v01").contains("reference_passes")
                .contains("studentExactlyAtPassingPointsPassed");
    }

    @Test
    void metadataProblemsAreReportedWithoutRunningTheSandbox() throws Exception {
        Path tasks = copyBank();
        Path taskYaml = tasks.resolve(VARIANT).resolve("task.yaml");
        Files.writeString(taskYaml, Files.readString(taskYaml).replace("code: T00-v01", "code: T00-v02"));

        Result validate = cli("validate", tasks.resolve("java").toString());

        assertThat(validate.code()).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(validate.output()).contains("schema").contains("ожидается T00-v01");
        assertThat(ReportFiles.read(tasks.resolve(VARIANT)).orElseThrow().runs()).isNull();
    }

    @Test
    void calibrationBlockPassesWithoutStarterFailure() throws Exception {
        Path tasks = copyBank(CALIBRATION_TEMPLATE, CALIBRATION_VARIANT);

        Result validate = cli("validate", tasks.resolve("java").toString(), "--runs", "1");

        assertThat(validate.code()).as(validate.output()).isEqualTo(TaskBankCli.EXIT_OK);
        assertThat(validate.output()).contains("PASSED CAL-v01");
        ValidationReport report = ReportFiles.read(tasks.resolve(CALIBRATION_VARIANT)).orElseThrow();
        assertThat(report.checks()).filteredOn(check -> check.id().equals("starter_fails"))
                .singleElement()
                .satisfies(check -> assertThat(check.details()).contains("не требуется"));
        assertThat(report.runs().referencePassed()).isEqualTo(1);
    }

    private Path copyBank() throws IOException {
        return copyBank("java/T00-example", VARIANT);
    }

    private Path copyBank(String template, String variant) throws IOException {
        Path target = temp.resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), target.resolve("schema"));
        copyTree(REPO_TASKS.resolve(template), target.resolve(template));
        Files.deleteIfExists(target.resolve(variant).resolve(VariantSources.VALIDATION_FILE));
        return target;
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

    private record Result(int code, String output) {
    }

    private static Result cli(String... args) {
        var buffer = new ByteArrayOutputStream();
        int code = TaskBankCli.run(args, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new Result(code, buffer.toString(StandardCharsets.UTF_8));
    }
}
