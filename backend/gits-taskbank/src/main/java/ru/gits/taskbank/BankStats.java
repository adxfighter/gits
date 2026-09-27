package ru.gits.taskbank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.sandbox.ExpectedTests;
import ru.gits.taskbank.TaskBankLayout.VariantLocation;
import ru.gits.taskbank.TaskSpecs.TemplateSpec;
import ru.gits.taskbank.TaskSpecs.VariantSpec;
import ru.gits.taskbank.check.SchemaCheck;

/**
 * Summary of the task bank built only from its files: task.yaml, template.yaml, tests, validation.json and
 * REVIEW.md. Rendering is deterministic (no dates), so the committed CATALOG.md can be regenerated and compared.
 */
public final class BankStats {

    /** Format example, not part of the bank. */
    static final String EXAMPLE_TEMPLATE = "T00";
    static final String REVIEW_FILE = "REVIEW.md";

    private static final List<String> LEVELS = List.of("junior", "middle", "senior");
    private static final List<String> REVIEW_CRITERIA =
            List.of("ясность", "реалистичность", "уровень", "скрытые тесты", "уникальность", "русский язык");
    private static final Pattern REVIEW_ROW =
            Pattern.compile("^\\|\\s*((?:T\\d{2}|CAL)-v\\d{2})\\s*\\|\\s*\\w+\\s*\\|((?:\\s*[1-5]\\s*\\|){6})");

    /** One variant of the bank. {@code reviewScores} are empty when REVIEW.md has no row for it. */
    public record Row(
            String code,
            String template,
            String templateTitle,
            String domain,
            String level,
            boolean calibration,
            List<String> competencies,
            String parameters,
            int visibleTests,
            int hiddenTests,
            Optional<Long> referenceMaxMs,
            Optional<String> validationProblem,
            List<Integer> reviewScores) {

        int minReviewScore() {
            return reviewScores.stream().mapToInt(Integer::intValue).min().orElse(0);
        }
    }

    private final List<Row> rows;
    private final Map<String, String> competencyTitles;

    private BankStats(List<Row> rows, Map<String, String> competencyTitles) {
        this.rows = rows;
        this.competencyTitles = competencyTitles;
    }

    public List<Row> rows() {
        return rows;
    }

    /** Reads every variant under {@code root} (tasks/java) except the format example. */
    public static BankStats collect(Path root) {
        var layout = new TaskBankLayout(root);
        Map<String, List<Integer>> review = readReview(root.resolve(REVIEW_FILE));
        Map<Path, TemplateSpec> templates = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        for (VariantLocation location : layout.variants()) {
            TemplateSpec template = templates.computeIfAbsent(location.templateDir(), VariantValidator::readTemplate);
            if (EXAMPLE_TEMPLATE.equals(template.code())) {
                continue;
            }
            VariantSpec spec = VariantValidator.readSpec(location.variantDir());
            VariantSources sources = VariantSources.read(location.variantDir());
            rows.add(new Row(
                    spec.code(),
                    template.code(),
                    template.title(),
                    spec.domain(),
                    spec.level(),
                    spec.isCalibration(),
                    template.competencies(),
                    parameters(spec.difficultyParams()),
                    ExpectedTests.of(sources.visibleTestFiles()).methods().size(),
                    ExpectedTests.of(sources.hiddenTestFiles()).methods().size(),
                    ReportFiles.read(location.variantDir()).map(ValidationReport::runs).map(ValidationReport.Runs::referenceMaxMs),
                    ReportFiles.problemWithStoredReport(location.variantDir()),
                    review.getOrDefault(spec.code(), List.of())));
        }
        rows.sort(Comparator.comparing(Row::calibration).thenComparing(Row::code));
        return new BankStats(List.copyOf(rows), readCompetencyTitles(root.toAbsolutePath().getParent()));
    }

    /** Distributions printed by {@code gits-taskbank stats}. */
    public String summary() {
        List<Row> tasks = tasks();
        var out = new StringBuilder();
        out.append("Вариантов задач: ").append(tasks.size())
                .append(", калибровочных: ").append(rows.size() - tasks.size()).append('\n');
        out.append("По уровням: ").append(LEVELS.stream()
                .map(level -> level + " " + count(tasks, row -> row.level().equals(level)))
                .collect(Collectors.joining(", "))).append('\n');
        out.append("По шаблонам:\n");
        byTemplate(tasks).forEach((template, list) -> out.append("  ").append(template).append(": ")
                .append(levelCounts(list)).append('\n'));
        out.append("По компетенциям:\n");
        byCompetency(tasks).forEach((competency, list) -> out.append("  ").append(competency).append(": ")
                .append(list.size()).append(" (").append(levelCounts(list)).append(")\n"));
        out.append("По доменам: ").append(byKey(tasks, Row::domain).entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue().size())
                .collect(Collectors.joining(", "))).append('\n');
        long invalid = rows.stream().filter(row -> row.validationProblem().isPresent()).count();
        out.append("Без действующей валидации: ").append(invalid).append('\n');
        return out.toString();
    }

    /** Full CATALOG.md. */
    public String catalog() {
        List<Row> tasks = tasks();
        var md = new StringBuilder();
        md.append("# Каталог банка задач\n\n");
        md.append("Файл создаётся командой `gits-taskbank stats tasks/java --catalog tasks/java/CATALOG.md` ")
                .append("по task.yaml, template.yaml, тестам, validation.json и REVIEW.md. Не редактируйте его вручную.\n\n");
        md.append("Вариантов задач: ").append(tasks.size()).append(", калибровочных: ")
                .append(rows.size() - tasks.size()).append(".\n\n");

        md.append("## Варианты\n\n");
        md.append("Оценки ревью (1–5): ").append(String.join(" / ", REVIEW_CRITERIA)).append(".\n\n");
        md.append("| Код | Шаблон | Домен | Уровень | Компетенции | Параметры сложности | Видимых | Скрытых | Прогон, мс | Ревью |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            md.append("| ").append(row.code())
                    .append(" | ").append(row.template())
                    .append(" | ").append(row.domain())
                    .append(" | ").append(row.calibration() ? "калибровка" : row.level())
                    .append(" | ").append(String.join(", ", row.competencies()))
                    .append(" | ").append(row.parameters())
                    .append(" | ").append(row.visibleTests())
                    .append(" | ").append(row.hiddenTests())
                    .append(" | ").append(row.referenceMaxMs().map(String::valueOf).orElse("—"))
                    .append(" | ").append(row.reviewScores().isEmpty() ? "—"
                            : row.reviewScores().stream().map(String::valueOf).collect(Collectors.joining("/")))
                    .append(" |\n");
        }

        md.append("\n## Распределение по уровням\n\n");
        md.append("| Шаблон | Название | junior | middle | senior | Всего |\n|---|---|---|---|---|---|\n");
        byTemplate(tasks).forEach((template, list) -> {
            md.append("| ").append(template).append(" | ").append(list.get(0).templateTitle());
            LEVELS.forEach(level -> md.append(" | ").append(count(list, row -> row.level().equals(level))));
            md.append(" | ").append(list.size()).append(" |\n");
        });
        md.append("| **Итого** | ");
        LEVELS.forEach(level -> md.append(" | ").append(count(tasks, row -> row.level().equals(level))));
        md.append(" | ").append(tasks.size()).append(" |\n");

        md.append("\n## Распределение по компетенциям\n\n");
        md.append("| Компетенция | Название | junior | middle | senior | Всего |\n|---|---|---|---|---|---|\n");
        byCompetency(tasks).forEach((competency, list) -> {
            md.append("| ").append(competency).append(" | ").append(competencyTitles.getOrDefault(competency, "—"));
            LEVELS.forEach(level -> md.append(" | ").append(count(list, row -> row.level().equals(level))));
            md.append(" | ").append(list.size()).append(" |\n");
        });

        md.append("\n## Распределение по доменам\n\n| Домен | Вариантов |\n|---|---|\n");
        byKey(tasks, Row::domain).forEach((domain, list) ->
                md.append("| ").append(domain).append(" | ").append(list.size()).append(" |\n"));

        md.append("\n## Известные ограничения\n\n");
        rows.stream().filter(row -> row.validationProblem().isPresent()).forEach(row ->
                md.append("- ").append(row.code()).append(": ").append(row.validationProblem().get()).append(".\n"));
        rows.stream().filter(row -> row.reviewScores().isEmpty()).forEach(row ->
                md.append("- ").append(row.code()).append(": нет оценок в REVIEW.md.\n"));
        rows.stream().filter(row -> !row.reviewScores().isEmpty() && row.minReviewScore() < 3).forEach(row ->
                md.append("- ").append(row.code()).append(": оценка ревью ниже 3.\n"));
        md.append("- Решение кандидата выполняется в одной JVM с тестами JUnit; целостность результата ")
                .append("обеспечивается изоляцией песочницы и сверкой отчёта (docs/adr/0004-runner.md).\n");
        md.append("- Многопоточные задачи проверяются многократными прогонами (`flaky_policy`); ")
                .append("редкий провал эталона на перегруженной машине возможен и выявляется повторной валидацией.\n");
        md.append("- Требования «без глобальной блокировки» и стиль кода проверяются не только тестами, ")
                .append("но и пунктами рубрики для ручной проверки.\n");
        return md.toString();
    }

    /** Writes CATALOG.md; returns true when the file content changed. */
    public boolean writeCatalog(Path file) {
        if (isCatalogCurrent(file)) {
            return false;
        }
        try {
            Files.writeString(file, catalog());
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /** True when the file equals the generated catalog (line endings are ignored). */
    public boolean isCatalogCurrent(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.readString(file).replace("\r\n", "\n").equals(catalog());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private List<Row> tasks() {
        return rows.stream().filter(row -> !row.calibration()).toList();
    }

    private static Map<String, List<Row>> byTemplate(List<Row> rows) {
        return byKey(rows, Row::template);
    }

    private static Map<String, List<Row>> byCompetency(List<Row> rows) {
        Map<String, List<Row>> result = new TreeMap<>();
        rows.forEach(row -> row.competencies()
                .forEach(competency -> result.computeIfAbsent(competency, key -> new ArrayList<>()).add(row)));
        return result;
    }

    private static Map<String, List<Row>> byKey(List<Row> rows, Function<Row, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }

    private static String levelCounts(List<Row> rows) {
        return LEVELS.stream().map(level -> level + " " + count(rows, row -> row.level().equals(level)))
                .collect(Collectors.joining(", "));
    }

    private static long count(List<Row> rows, java.util.function.Predicate<Row> predicate) {
        return rows.stream().filter(predicate).count();
    }

    private static String parameters(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "—";
        }
        return params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /** Scores from the review table: | code | level | 6 scores | comment |. */
    static Map<String, List<Integer>> readReview(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, List<Integer>> scores = new TreeMap<>();
        try {
            for (String line : Files.readAllLines(file)) {
                var matcher = REVIEW_ROW.matcher(line);
                if (matcher.find()) {
                    List<Integer> values = Pattern.compile("\\|").splitAsStream(matcher.group(2))
                            .map(String::strip).filter(cell -> !cell.isEmpty()).map(Integer::valueOf).toList();
                    scores.put(matcher.group(1), values);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return scores;
    }

    private static Map<String, String> readCompetencyTitles(Path tasksDir) {
        Path file = tasksDir.resolve("competencies.yaml");
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, String> titles = new TreeMap<>();
        JsonNode competencies = SchemaCheck.readYaml(file).path("competencies");
        competencies.fieldNames().forEachRemaining(code -> titles.put(code, competencies.path(code).path("title").asText()));
        return titles;
    }
}
