package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.gits.sandbox.ExpectedTests;

/**
 * stats on a synthetic bank made of copies of the format example: T00 itself (left out), task templates T02 and T01
 * (T02 keeps its validation.json) and a calibration template CAL.
 */
class BankStatsTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final String EXAMPLE_VARIANT = "java/T00-example/variants/v01";
    private static final String REVIEW_HEADER = String.join("\n",
            "| code | level | clarity | realism | level-fit | hidden-tests | uniqueness | russian | comment |",
            "|---|---|---|---|---|---|---|---|---|");

    @TempDir
    Path temp;

    @Test
    void exampleTemplateIsLeftOutAndRowsAreOrdered() throws IOException {
        BankStats stats = BankStats.collect(syntheticBank(defaultReview()));

        assertThat(stats.rows()).extracting(BankStats.Row::code).containsExactly("T01-v01", "T02-v01", "CAL-v01");
        assertThat(stats.rows()).extracting(BankStats.Row::calibration).containsExactly(false, false, true);
    }

    @Test
    void testCountsAndRunTimeComeFromTheFiles() throws IOException {
        Path example = REPO_TASKS.resolve(EXAMPLE_VARIANT);
        VariantSources sources = VariantSources.read(example);
        int visible = ExpectedTests.of(sources.visibleTestFiles()).methods().size();
        int hidden = ExpectedTests.of(sources.hiddenTestFiles()).methods().size();
        long runTime = ReportFiles.read(example).orElseThrow().runs().referenceMaxMs();

        BankStats stats = BankStats.collect(syntheticBank(defaultReview()));

        BankStats.Row withReport = stats.rows().get(1);
        assertThat(withReport.code()).isEqualTo("T02-v01");
        assertThat(withReport.visibleTests()).isEqualTo(visible);
        assertThat(withReport.hiddenTests()).isEqualTo(hidden);
        assertThat(withReport.referenceMaxMs()).contains(runTime);
        assertThat(stats.catalog()).contains("| " + visible + " | " + hidden + " | " + runTime + " | 4/4/4/4/4/4 |");

        BankStats.Row withoutReport = stats.rows().get(0);
        assertThat(withoutReport.referenceMaxMs()).isEmpty();
        assertThat(withoutReport.validationProblem()).isPresent();
        assertThat(withoutReport.reviewScores()).containsExactly(5, 4, 3, 5, 4, 5);
    }

    @Test
    void summaryAndCatalogShowDistributionsWithoutTheCalibrationBlock() throws IOException {
        BankStats stats = BankStats.collect(syntheticBank(defaultReview()));

        assertThat(stats.summary())
                .contains("Вариантов задач: 2, калибровочных: 1")
                .contains("По уровням: junior 2, middle 0, senior 0")
                .contains("java.basics.conditions: 2");
        assertThat(stats.catalog())
                .contains("| CAL-v01 | CAL | образование | калибровка |")
                .contains("| не оценивается |")
                .contains("| **Итого** |  | 2 | 0 | 0 | 2 |")
                .contains("T01-v01: нет validation.json")
                .doesNotContain("CAL-v01: нет оценок");
        assertThat(stats.reviewProblems()).isEmpty();
    }

    @Test
    void reviewRowsAreParsedStrictly() throws IOException {
        Path bank = syntheticBank(String.join("\n", REVIEW_HEADER,
                "| T01-v01 | junior | 5 | 5 | 5 | 5 | 5 | 5 | first |",
                "| T01-v01 | junior | 4 | 4 | 4 | 4 | 4 | 4 | repeated |",
                "| T02-v01 | junior | 4 | 4 | 3→4 | 4 | 4 | 4 | arrow |",
                "| T09-v01 | middle | 4 | 4 | 4 | 4 | 4 | 4 | not in the bank |",
                "",
                "| Вариант | Результат |",
                "|---|---|",
                "| T01-v01 | проходит |"));

        BankStats stats = BankStats.collect(bank);

        assertThat(stats.reviewProblems()).containsExactly(
                "REVIEW.md, строка 4: повторные оценки T01-v01",
                "REVIEW.md, строка 5: оценки T02-v01 не разобраны",
                "REVIEW.md: оценки для T09-v01, которого нет в банке");
        assertThat(stats.rows().get(0).reviewScores()).containsExactly(5, 5, 5, 5, 5, 5);
        assertThat(cli("stats", bank.toString()).code()).isEqualTo(TaskBankCli.EXIT_FAILED);
    }

    @Test
    void cliWritesTheCatalogAndChecksIt() throws IOException {
        Path bank = syntheticBank(defaultReview());
        Path catalog = bank.resolve("CATALOG.md");

        Result missing = cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check");
        assertThat(missing.code()).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(missing.output()).contains("Каталог не найден");

        Result write = cli("stats", bank.toString(), "--catalog", catalog.toString());
        assertThat(write.code()).as(write.output()).isEqualTo(TaskBankCli.EXIT_OK);
        assertThat(write.output()).contains("Каталог обновлён");
        assertThat(cli("stats", bank.toString(), "--catalog", catalog.toString()).output())
                .contains("Каталог не изменился");

        assertThat(cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check").code())
                .isEqualTo(TaskBankCli.EXIT_OK);

        // A checkout with CRLF line endings is still current
        Files.writeString(catalog, Files.readString(catalog).replace("\n", "\r\n"));
        assertThat(cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check").code())
                .isEqualTo(TaskBankCli.EXIT_OK);

        Files.writeString(catalog, Files.readString(catalog) + "\nручная правка\n");
        Result stale = cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check");
        assertThat(stale.code()).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(stale.output()).contains("Каталог устарел");
    }

    @Test
    void optionsThatDoNotApplyAreRejected() {
        assertThat(cli("stats", temp.toString(), "--check").code()).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(cli("stats", temp.toString(), "--variant", "T01").code()).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(cli("verify-hashes", temp.toString(), "--catalog", "x.md").code()).isEqualTo(TaskBankCli.EXIT_USAGE);
    }

    private static String defaultReview() {
        return String.join("\n", REVIEW_HEADER,
                "| T01-v01 | junior | 5 | 4 | 3 | 5 | 4 | 5 | sample |",
                "| T02-v01 | junior | 4 | 4 | 4 | 4 | 4 | 4 | sample |");
    }

    /** tasks/{schema, competencies.yaml, java/{T00-example, T01-sample, T02-sample, CAL-sample, REVIEW.md}}. */
    private Path syntheticBank(String review) throws IOException {
        Path tasks = temp.resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), tasks.resolve("schema"));
        Files.copy(REPO_TASKS.resolve("competencies.yaml"), tasks.resolve("competencies.yaml"));
        Path java = tasks.resolve("java");
        copyTree(REPO_TASKS.resolve("java/T00-example"), java.resolve("T00-example"));
        copyExampleAs(java, "T02-sample", "T02", true, false);
        copyExampleAs(java, "T01-sample", "T01", false, false);
        copyExampleAs(java, "CAL-sample", "CAL", false, true);
        Files.writeString(java.resolve(BankStats.REVIEW_FILE), review);
        return java;
    }

    private static void copyExampleAs(Path java, String directory, String code, boolean keepReport, boolean calibration)
            throws IOException {
        Path copy = java.resolve(directory);
        copyTree(REPO_TASKS.resolve("java/T00-example"), copy);
        if (!keepReport) {
            Files.deleteIfExists(copy.resolve("variants/v01").resolve(VariantSources.VALIDATION_FILE));
        }
        for (Path yaml : List.of(copy.resolve("template.yaml"), copy.resolve("variants/v01/task.yaml"))) {
            String text = Files.readString(yaml).replace("T00", code);
            if (calibration) {
                text = text.replace("kind: task", "kind: calibration");
            }
            Files.writeString(yaml, text);
        }
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
