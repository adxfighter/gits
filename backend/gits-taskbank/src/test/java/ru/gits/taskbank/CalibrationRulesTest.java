package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rules for kind calibration that are checked before any sandbox run, so no Docker is needed: every case here must
 * fail in the schema or files check and leave no run statistics.
 */
class CalibrationRulesTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final String EXAMPLE = "java/T00-example";
    private static final String EXAMPLE_VARIANT = EXAMPLE + "/variants/v01";
    private static final String CALIBRATION = "java/CAL-calibration";
    private static final String CALIBRATION_VARIANT = CALIBRATION + "/variants/v01";

    @TempDir
    Path temp;

    @Test
    void calibrationBlockWithHiddenTestsIsRejected() throws IOException {
        Path tasks = copyBank(CALIBRATION, CALIBRATION_VARIANT);
        Path helper = tasks.resolve(CALIBRATION_VARIANT)
                .resolve("tests-hidden/src/test/java/ru/gits/task/telecom/cal/Helper.java");
        Files.createDirectories(helper.getParent());
        Files.writeString(helper, "package ru.gits.task.telecom.cal;\n\nclass Helper {\n}\n");

        assertRejected(tasks, CALIBRATION_VARIANT, "FAILED CAL-v01", "не содержит скрытых тестов");
    }

    @Test
    void calibrationBlockWithoutSolutionIsRejected() throws IOException {
        Path tasks = copyBank(CALIBRATION, CALIBRATION_VARIANT);
        deleteTree(tasks.resolve(CALIBRATION_VARIANT).resolve("solution"));

        assertRejected(tasks, CALIBRATION_VARIANT, "FAILED CAL-v01", "нет solution/");
    }

    @Test
    void kindCalibrationOutsideTheCalibrationTemplateIsRejected() throws IOException {
        Path tasks = copyBank(EXAMPLE, EXAMPLE_VARIANT);
        replaceInTaskYaml(tasks.resolve(EXAMPLE_VARIANT), "kind: task", "kind: calibration", "code: T00-v01");

        assertRejected(tasks, EXAMPLE_VARIANT, "FAILED T00-v01", "допустим только в шаблоне CAL");
    }

    @Test
    void calibrationTemplateVariantMustKeepKindCalibration() throws IOException {
        Path tasks = copyBank(CALIBRATION, CALIBRATION_VARIANT);
        replaceInTaskYaml(tasks.resolve(CALIBRATION_VARIANT), "kind: calibration", "kind: task", null);

        assertRejected(tasks, CALIBRATION_VARIANT, "FAILED CAL-v01", "должны иметь kind: calibration");
    }

    @Test
    void calibrationBlockIsShort() throws IOException {
        Path tasks = copyBank(CALIBRATION, CALIBRATION_VARIANT);
        replaceInTaskYaml(tasks.resolve(CALIBRATION_VARIANT), "time_limit_min: 5", "time_limit_min: 30", null);

        assertRejected(tasks, CALIBRATION_VARIANT, "FAILED CAL-v01", "не более 10 минут");
    }

    private static void assertRejected(Path tasks, String variant, String status, String reason) {
        var buffer = new ByteArrayOutputStream();
        int code = TaskBankCli.run(new String[] {"validate", tasks.resolve("java").toString()},
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertThat(code).as(output).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(output).contains(status).contains(reason);
        assertThat(ReportFiles.read(tasks.resolve(variant)).orElseThrow().runs())
                .as("rejected before the sandbox").isNull();
    }

    /**
     * Replaces {@code from} with {@code to} in task.yaml; when the variant has no explicit kind yet, the line is
     * inserted after {@code anchor}.
     */
    private static void replaceInTaskYaml(Path variantDir, String from, String to, String anchor) throws IOException {
        Path taskYaml = variantDir.resolve("task.yaml");
        String yaml = Files.readString(taskYaml);
        if (yaml.contains(from)) {
            yaml = yaml.replace(from, to);
        } else if (anchor != null) {
            yaml = yaml.replace(anchor, anchor + "\n" + to);
        } else {
            throw new IllegalStateException(from + " not found in " + taskYaml);
        }
        Files.writeString(taskYaml, yaml);
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

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(path);
            }
        }
    }
}
