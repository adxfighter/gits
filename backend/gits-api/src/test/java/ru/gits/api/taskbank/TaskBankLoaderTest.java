package ru.gits.api.taskbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.gits.api.config.GitsProperties;
import ru.gits.api.support.TestcontainersConfiguration;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.task.VariantStatus;
import ru.gits.taskbank.ContentHash;
import ru.gits.taskbank.ReportFiles;
import ru.gits.taskbank.TaskBankCli;
import ru.gits.taskbank.ValidationReport;
import ru.gits.taskbank.VariantSources;
import ru.gits.taskbank.load.TaskBankLoader;

/**
 * P06 acceptance on a copy of tasks/java/T00-example: first load, repeated load, a file changed without
 * re-validation, a re-validated change and a removed variant. The property gives this class its own Spring context
 * and therefore its own PostgreSQL container, so the global "disable missing variants" step sees only these tasks.
 */
@SpringBootTest(properties = "gits.test.context=taskbank-loader")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TaskBankLoaderTest {

    private static final Path REPO_TASKS = Path.of("../../tasks");
    private static final String VARIANT = "java/T00-example/variants/v01";
    private static final String CODE = "T00-v01";

    @Autowired TaskTemplateRepository templates;
    @Autowired TaskVariantRepository variants;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired GitsProperties properties;
    @Autowired PostgreSQLContainer<?> postgres;

    @TempDir
    Path temp;

    private Path tasks;
    private Instant now = Instant.parse("2026-09-01T10:00:00Z");

    @BeforeEach
    void cleanBankAndCopyExample() throws IOException {
        jdbc.update("DELETE FROM task_file");
        jdbc.update("DELETE FROM task_variant");
        jdbc.update("DELETE FROM task_template");
        tasks = temp.resolve("tasks");
        copyTree(REPO_TASKS.resolve("schema"), tasks.resolve("schema"));
        copyTree(REPO_TASKS.resolve("java/T00-example"), tasks.resolve("java/T00-example"));
    }

    private TaskBankLoader.Summary load() {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new TaskBankLoader(templates, variants, transactionManager, clock, Set.of()).load(tasks.resolve("java"));
    }

    @Test
    void firstLoadStoresTemplateVariantAndAllFiles() {
        TaskBankLoader.Summary summary = load();

        assertThat(summary.loaded()).isEqualTo(1);
        assertThat(summary.updated() + summary.unchanged() + summary.skipped() + summary.disabled()).isZero();
        VariantSources sources = VariantSources.read(tasks.resolve(VARIANT));
        inTransaction(() -> {
            TaskVariant variant = variants.findByCode(CODE).orElseThrow();
            assertThat(variant.getStatus()).isEqualTo(VariantStatus.VALIDATED);
            assertThat(variant.getKind()).isEqualTo(TaskKind.TASK);
            assertThat(variant.getTemplate().getCode()).isEqualTo("T00");
            assertThat(variant.getContentHash()).isEqualTo(ContentHash.of(sources.allFiles()));
            assertThat(variant.getStatementMd()).isEqualTo(sources.statement());
            assertThat(variant.getValidationReport()).contains("PASSED");
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() == FileKind.HIDDEN_TEST)
                    .hasSize(sources.hiddenTests().size());
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() == FileKind.VISIBLE_TEST)
                    .hasSize(sources.visibleTests().size());
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() == FileKind.SOLUTION)
                    .hasSize(sources.solution().size());
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() == FileKind.STARTER)
                    .isNotEmpty()
                    .allSatisfy(file -> assertThat(file.isEditable()).isTrue());
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() == FileKind.STARTER
                            || file.getKind() == FileKind.READONLY)
                    .hasSize(sources.starter().size());
            // only the candidate's editable starter files are editable; solution and tests never are
            assertThat(variant.getFiles()).filteredOn(file -> file.getKind() != FileKind.STARTER)
                    .allSatisfy(file -> assertThat(file.isEditable()).isFalse());
            assertThat(variant.getDifficultyParams()).contains("defect_type");
            assertThat(variant.getTemplate().getCompetencies()).contains("java.basics.conditions");
            assertThat(variant.getTemplate().getDifficultyModel()).contains("defects_count");
            assertThat(variant.getFiles()).extracting(TaskFile::getPath)
                    .allSatisfy(path -> assertThat(path).startsWith("src/"));
        });
    }

    @Test
    void repeatedLoadChangesNothing() {
        load();
        Instant firstUpdate = updatedAt();
        now = now.plusSeconds(3600);

        TaskBankLoader.Summary summary = load();

        assertThat(summary.unchanged()).isEqualTo(1);
        assertThat(summary.loaded() + summary.updated() + summary.skipped() + summary.disabled()).isZero();
        assertThat(updatedAt()).isEqualTo(firstUpdate);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_template", Integer.class)).isEqualTo(1);
    }

    @Test
    void fileChangedWithoutRevalidationIsSkippedAndTheStoredVersionKept() throws IOException {
        load();
        String storedHash = variants.findByCode(CODE).orElseThrow().getContentHash();
        Path statement = tasks.resolve(VARIANT).resolve("statement.md");
        Files.writeString(statement, Files.readString(statement) + "\nНепроверенная правка.");

        TaskBankLoader.Summary summary = load();

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.warnings()).singleElement().asString().contains(CODE).contains("content_hash");
        TaskVariant variant = variants.findByCode(CODE).orElseThrow();
        assertThat(variant.getStatus()).isEqualTo(VariantStatus.VALIDATED);
        assertThat(variant.getContentHash()).isEqualTo(storedHash);
        assertThat(variant.getStatementMd()).doesNotContain("Непроверенная правка");
    }

    @Test
    void revalidatedChangeReplacesTheVariantAndItsFiles() throws IOException {
        load();
        Path variantDir = tasks.resolve(VARIANT);
        Path statement = variantDir.resolve("statement.md");
        Files.writeString(statement, Files.readString(statement) + "\nПроверенная правка.");
        markRevalidated(variantDir);
        now = now.plusSeconds(3600);

        TaskBankLoader.Summary summary = load();

        assertThat(summary.updated()).isEqualTo(1);
        inTransaction(() -> {
            TaskVariant variant = variants.findByCode(CODE).orElseThrow();
            assertThat(variant.getStatementMd()).contains("Проверенная правка");
            assertThat(variant.getUpdatedAt()).isEqualTo(now);
            assertThat(variant.getFiles()).hasSize(countFiles(variantDir));
        });
    }

    @Test
    void removedVariantIsDisabledAndComesBackWhenRestored() throws IOException {
        addSecondVariant();  // the bank must not become empty: an empty bank is an error, not a mass disable
        assertThat(load().loaded()).isEqualTo(2);
        Path variantDir = tasks.resolve(VARIANT);
        Path backup = temp.resolve("backup");
        copyTree(variantDir, backup);
        deleteTree(variantDir);

        TaskBankLoader.Summary summary = load();

        assertThat(summary.disabled()).isEqualTo(1);
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.DISABLED);

        copyTree(backup, variantDir);
        TaskBankLoader.Summary restored = load();

        assertThat(restored.updated()).isEqualTo(1);
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.VALIDATED);
        assertThat(variants.findByCode("T00-v02").orElseThrow().getStatus()).isEqualTo(VariantStatus.VALIDATED);
    }

    /** T00-v02: a copy of v01 with its own code and a report for its files, as validate would write. */
    private void addSecondVariant() throws IOException {
        Path second = tasks.resolve("java/T00-example/variants/v02");
        copyTree(tasks.resolve(VARIANT), second);
        Path taskYaml = second.resolve("task.yaml");
        Files.writeString(taskYaml, Files.readString(taskYaml).replace("code: T00-v01", "code: T00-v02"));
        markRevalidated(second);
    }

    @Test
    void excludedTemplateIsNotLoadedAndLoadedCopiesAreDisabled() {
        load();

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TaskBankLoader.Summary summary = new TaskBankLoader(templates, variants, transactionManager, clock, Set.of("T00"))
                .load(tasks.resolve("java"));

        assertThat(summary.loaded() + summary.unchanged()).isZero();
        assertThat(summary.disabled()).isEqualTo(1);
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.DISABLED);
    }

    @Test
    void emptyBankIsAnErrorAndDisablesNothing() throws IOException {
        load();
        deleteTree(tasks.resolve("java/T00-example"));

        assertThatThrownBy(this::load).isInstanceOf(IllegalStateException.class).hasMessageContaining("нет ни одного");
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.VALIDATED);
    }

    @Test
    void templateWithoutTemplateYamlKeepsItsVariants() throws IOException {
        load();
        Files.delete(tasks.resolve("java/T00-example/template.yaml"));

        TaskBankLoader.Summary summary = load();

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.disabled()).isZero();
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.VALIDATED);
    }

    @Test
    void unreadableValidationReportIsSkippedNotFatal() throws IOException {
        load();
        Files.writeString(tasks.resolve(VARIANT).resolve(VariantSources.VALIDATION_FILE), "{ broken");

        TaskBankLoader.Summary summary = load();

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.disabled()).isZero();
        assertThat(variants.findByCode(CODE).orElseThrow().getStatus()).isEqualTo(VariantStatus.VALIDATED);
    }

    @Test
    void templateChangeIsWrittenWithoutTouchingTheVariant() throws IOException {
        load();
        Instant variantUpdate = updatedAt();
        Path templateYaml = tasks.resolve("java/T00-example/template.yaml");
        String title = "Граничное условие в подсчёте — новое название";
        Files.writeString(templateYaml, Files.readString(templateYaml)
                .replaceFirst("(?m)^title: .*$", "title: " + title));
        now = now.plusSeconds(60);

        TaskBankLoader.Summary summary = load();

        assertThat(summary.unchanged()).isEqualTo(1);
        assertThat(templates.findByCode("T00").orElseThrow().getTitle()).isEqualTo(title);
        assertThat(updatedAt()).isEqualTo(variantUpdate);
    }

    @Test
    void startupLoaderExcludesTheExampleByDefaultAndLogsTheSummary(CapturedOutput output) {
        var taskbank = new GitsProperties.Taskbank(tasks.toString(), properties.taskbank().excludedTemplates());
        var startup = new TaskBankStartupLoader(withTaskbank(taskbank), templates, variants, transactionManager,
                Clock.fixed(now, ZoneOffset.UTC));

        startup.run(null);

        assertThat(properties.taskbank().excludedTemplates()).containsExactly("T00");
        assertThat(variants.findByCode(CODE)).isEmpty();
        assertThat(output).contains("Банк задач").contains("загружено 0");
    }

    @Test
    void startupLoaderStopsTheStartWhenTheBankIsMissing() {
        var taskbank = new GitsProperties.Taskbank(temp.resolve("nowhere").toString(), List.of());
        var startup = new TaskBankStartupLoader(withTaskbank(taskbank), templates, variants, transactionManager,
                Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> startup.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void cliLoadTakesTheDatabaseFromTheEnvironment() throws IOException {
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
        try {
            String bank = tasks.resolve("java").toString();
            assertThat(cli("load", bank, "--exclude", "").code()).isEqualTo(0);
            assertThat(variants.findByCode(CODE)).isPresent();

            Path statement = tasks.resolve(VARIANT).resolve("statement.md");
            Files.writeString(statement, Files.readString(statement) + "\nНепроверенная правка.");
            Cli skipped = cli("load", bank, "--exclude", "");
            assertThat(skipped.code()).isEqualTo(1);
            assertThat(skipped.output()).contains("пропущено 1");

            assertThat(cli("load", temp.resolve("nowhere").toString()).code()).isEqualTo(2);
        } finally {
            System.clearProperty("spring.datasource.url");
            System.clearProperty("spring.datasource.username");
            System.clearProperty("spring.datasource.password");
        }
    }

    private record Cli(int code, String output) {
    }

    private static Cli cli(String... args) {
        var buffer = new ByteArrayOutputStream();
        int code = TaskBankCli.run(args, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new Cli(code, buffer.toString(StandardCharsets.UTF_8));
    }

    private GitsProperties withTaskbank(GitsProperties.Taskbank taskbank) {
        return new GitsProperties(properties.publicBaseUrl(), properties.inviteTtl(), properties.security(),
                properties.demo(), taskbank);
    }

    /** Writes a PASSED report for the current files, as a successful validate run would. */
    private static void markRevalidated(Path variantDir) {
        ValidationReport old = ReportFiles.read(variantDir).orElseThrow();
        String hash = ContentHash.of(VariantSources.read(variantDir).allFiles());
        ReportFiles.write(variantDir, new ValidationReport(old.code(), old.status(), hash, old.validatedAt(),
                old.validatorVersion(), old.runs(), old.checks()));
    }

    private Instant updatedAt() {
        return variants.findByCode(CODE).orElseThrow().getUpdatedAt();
    }

    private void inTransaction(Runnable assertions) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> assertions.run());
    }

    private static int countFiles(Path variantDir) {
        VariantSources sources = VariantSources.read(variantDir);
        return sources.starter().size() + sources.solution().size() + sources.visibleTests().size()
                + sources.hiddenTests().size();
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
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
