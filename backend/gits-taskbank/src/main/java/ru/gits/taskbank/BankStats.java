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
import java.util.function.Predicate;
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
    /** Start of a score row: | code | level |. A line that starts like this must parse completely. */
    private static final Pattern REVIEW_ROW_START =
            Pattern.compile("^\\|\\s*((?:T\\d{2}|CAL)-v\\d{2})\\s*\\|\\s*(junior|middle|senior)\\s*\\|");
    private static final Pattern REVIEW_ROW =
            Pattern.compile("^\\|\\s*((?:T\\d{2}|CAL)-v\\d{2})\\s*\\|\\s*(?:junior|middle|senior)\\s*\\|((?:\\s*[1-5]\\s*\\|){6})");
    private static final Pattern CELL_SEPARATOR = Pattern.compile("\\|");

    /** Scores by variant code and every problem met while reading REVIEW.md. */
    record Review(Map<String, List<Integer>> scores, List<String> problems) {
    }

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
    private final List<String> reviewProblems;

    private BankStats(List<Row> rows, Map<String, String> competencyTitles, List<String> reviewProblems) {
        this.rows = rows;
        this.competencyTitles = competencyTitles;
        this.reviewProblems = reviewProblems;
    }

    public List<Row> rows() {
        return rows;
    }

    /** Problems of REVIEW.md: unparsed score rows, duplicates, codes that are not in the bank. */
    public List<String> reviewProblems() {
        return reviewProblems;
    }

    /** Reads every variant under {@code root} (tasks/java) except the format example. */
    public static BankStats collect(Path root) {
        var layout = new TaskBankLayout(root);
        Review reviewTable = readReview(root.resolve(REVIEW_FILE));
        Map<String, List<Integer>> review = reviewTable.scores();
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
        List<String> problems = new ArrayList<>(reviewTable.problems());
        var codes = rows.stream().map(Row::code).collect(Collectors.toSet());
        review.keySet().stream().filter(code -> !codes.contains(code))
                .forEach(code -> problems.add("REVIEW.md: оценки для " + code + ", которого нет в банке"));
        return new BankStats(List.copyOf(rows), readCompetencyTitles(layout.tasksDirectory()), List.copyOf(problems));
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
        reviewProblems.forEach(problem -> out.append("⚠ ").append(problem).append('\n'));
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
        md.append("Оценки ревью (1–5): ").append(String.join(" / ", REVIEW_CRITERIA)).append(".\n");
        md.append("Число тестов — число различных тестовых методов (параметризованный тест считается один раз), ")
                .append("так же считает валидатор. «Прогон, мс» — самый долгий прогон эталона при последней валидации.\n\n");
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
                    .append(" | ").append(reviewCell(row))
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
        rows.stream().filter(row -> !row.calibration() && row.reviewScores().isEmpty()).forEach(row ->
                md.append("- ").append(row.code()).append(": нет оценок в REVIEW.md.\n"));
        reviewProblems.forEach(problem -> md.append("- ").append(problem).append(".\n"));
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
        String content = catalog();
        if (matches(file, content)) {
            return false;
        }
        try {
            Files.writeString(file, content);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /** True when the file equals the generated catalog (line endings are ignored). */
    public boolean isCatalogCurrent(Path file) {
        return matches(file, catalog());
    }

    private static boolean matches(Path file, String content) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.readString(file).replace("\r\n", "\n").equals(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static String reviewCell(Row row) {
        if (row.reviewScores().isEmpty()) {
            return row.calibration() ? "не оценивается" : "—";
        }
        return row.reviewScores().stream().map(String::valueOf).collect(Collectors.joining("/"));
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

    private static long count(List<Row> rows, Predicate<Row> predicate) {
        return rows.stream().filter(predicate).count();
    }

    private static String parameters(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "—";
        }
        return params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Scores from the review table: | code | level | 6 scores | comment |. Lines of other tables do not start with
     * a code followed by a level and are ignored; a score row that does not parse or repeats a code is a problem.
     */
    static Review readReview(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Review(Map.of(), List.of());
        }
        Map<String, List<Integer>> scores = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file);
            for (int number = 1; number <= lines.size(); number++) {
                String line = lines.get(number - 1);
                var start = REVIEW_ROW_START.matcher(line);
                if (!start.find()) {
                    continue;
                }
                var row = REVIEW_ROW.matcher(line);
                if (!row.find()) {
                    problems.add("REVIEW.md, строка " + number + ": оценки " + start.group(1) + " не разобраны");
                    continue;
                }
                List<Integer> values = CELL_SEPARATOR.splitAsStream(row.group(2))
                        .map(String::strip).filter(cell -> !cell.isEmpty()).map(Integer::valueOf).toList();
                if (scores.putIfAbsent(row.group(1), values) != null) {
                    problems.add("REVIEW.md, строка " + number + ": повторные оценки " + row.group(1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return new Review(scores, List.copyOf(problems));
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
