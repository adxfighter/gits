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

/** stats on a small synthetic bank: the example template plus one copy of it registered as T01. */
class BankStatsTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");

    @TempDir
    Path temp;

    @Test
    void exampleTemplateIsLeftOutAndVariantDataIsCollected() throws IOException {
        Path bank = syntheticBank();

        BankStats stats = BankStats.collect(bank);

        assertThat(stats.rows()).singleElement().satisfies(row -> {
            assertThat(row.code()).isEqualTo("T01-v01");
            assertThat(row.level()).isEqualTo("junior");
            assertThat(row.competencies()).contains("java.basics.conditions");
            assertThat(row.visibleTests()).isBetween(2, 5);
            assertThat(row.hiddenTests()).isBetween(5, 15);
            assertThat(row.validationProblem()).isPresent();
            assertThat(row.reviewScores()).containsExactly(5, 4, 3, 5, 4, 5);
        });
    }

    @Test
    void summaryAndCatalogShowDistributions() throws IOException {
        BankStats stats = BankStats.collect(syntheticBank());

        assertThat(stats.summary())
                .contains("Вариантов задач: 1, калибровочных: 0")
                .contains("По уровням: junior 1, middle 0, senior 0")
                .contains("java.basics.conditions: 1");
        assertThat(stats.catalog())
                .contains("| T01-v01 | T01 | образование | junior |")
                .contains("| 5/4/3/5/4/5 |")
                .contains("| **Итого** |  | 1 | 0 | 0 | 1 |")
                .contains("T01-v01: нет validation.json");
    }

    @Test
    void reviewRowsAreParsedOnlyWithSixScores() throws IOException {
        Path review = temp.resolve("REVIEW.md");
        Files.writeString(review, String.join("\n",
                "| code | level | clarity | realism | level-fit | hidden-tests | uniqueness | russian | comment |",
                "|---|---|---|---|---|---|---|---|---|",
                "| T02-v03 | middle | 4 | 5 | 4 | 4 | 4 | 5 | ok |",
                "| CAL-v01 | junior | 5 | 5 | 5 | 5 | 5 | 5 | calibration |",
                "| T02-v04 | senior | 4 | 4 | n/a |"));

        assertThat(BankStats.readReview(review))
                .containsOnlyKeys("T02-v03", "CAL-v01")
                .containsEntry("T02-v03", List.of(4, 5, 4, 4, 4, 5));
    }

    @Test
    void cliWritesTheCatalogAndChecksIt() throws IOException {
        Path bank = syntheticBank();
        Path catalog = bank.resolve("CATALOG.md");

        assertThat(cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check").code())
                .isEqualTo(TaskBankCli.EXIT_FAILED);

        Result write = cli("stats", bank.toString(), "--catalog", catalog.toString());
        assertThat(write.code()).isEqualTo(TaskBankCli.EXIT_OK);
        assertThat(write.output()).contains("Каталог обновлён");
        assertThat(catalog).exists();

        Result check = cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check");
        assertThat(check.code()).as(check.output()).isEqualTo(TaskBankCli.EXIT_OK);

        Files.writeString(catalog, Files.readString(catalog) + "\nручная правка\n");
        assertThat(cli("stats", bank.toString(), "--catalog", catalog.toString(), "--check").code())
                .isEqualTo(TaskBankCli.EXIT_FAILED);
    }

    @Test
    void checkWithoutCatalogIsAUsageError() {
        assertThat(cli("stats", temp.toString(), "--check").code()).isEqualTo(TaskBankCli.EXIT_USAGE);
    }

    /** tasks/{schema, competencies.yaml, java/{T00-example, T01-sample, REVIEW.md}}; returns tasks/java. */
    private Path syntheticBank() throws IOException {
        Path tasks = temp.resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), tasks.resolve("schema"));
        Files.copy(REPO_TASKS.resolve("competencies.yaml"), tasks.resolve("competencies.yaml"));
        Path java = tasks.resolve("java");
        copyTree(REPO_TASKS.resolve("java/T00-example"), java.resolve("T00-example"));
        Path sample = java.resolve("T01-sample");
        copyTree(REPO_TASKS.resolve("java/T00-example"), sample);
        Files.deleteIfExists(sample.resolve("variants/v01").resolve(VariantSources.VALIDATION_FILE));
        for (Path yaml : List.of(sample.resolve("template.yaml"), sample.resolve("variants/v01/task.yaml"))) {
            Files.writeString(yaml, Files.readString(yaml).replace("T00", "T01"));
        }
        Files.writeString(java.resolve(BankStats.REVIEW_FILE), String.join("\n",
                "| code | level | clarity | realism | level-fit | hidden-tests | uniqueness | russian | comment |",
                "|---|---|---|---|---|---|---|---|---|",
                "| T01-v01 | junior | 5 | 4 | 3 | 5 | 4 | 5 | sample |"));
        return java;
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
