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

/** Validates copies of tasks/java/T00-example in real sandbox containers (needs Docker and the image). */
class TaskBankValidatorDockerTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final String VARIANT = "java/T00-example/variants/v01";

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

    private Path copyBank() throws IOException {
        Path target = temp.resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), target.resolve("schema"));
        copyTree(REPO_TASKS.resolve("java/T00-example"), target.resolve("java/T00-example"));
        Files.deleteIfExists(target.resolve(VARIANT).resolve(VariantSources.VALIDATION_FILE));
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
