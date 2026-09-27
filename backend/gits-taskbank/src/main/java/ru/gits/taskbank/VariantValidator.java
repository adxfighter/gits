package ru.gits.taskbank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.sandbox.ExpectedTests;
import ru.gits.sandbox.InvalidSourceException;
import ru.gits.sandbox.ParsedRun;
import ru.gits.sandbox.SandboxExecutor;
import ru.gits.sandbox.SandboxOutputParser;
import ru.gits.sandbox.SandboxRun;
import ru.gits.sandbox.SourceArchive;
import ru.gits.sandbox.SourceFile;
import ru.gits.sandbox.TestCaseResult;
import ru.gits.taskbank.TaskSpecs.TemplateSpec;
import ru.gits.taskbank.TaskSpecs.VariantSpec;
import ru.gits.taskbank.ValidationReport.Check;
import ru.gits.taskbank.check.ForbiddenConstructCheck;
import ru.gits.taskbank.check.LeakCheck;
import ru.gits.taskbank.check.SchemaCheck;

/** Runs the ten checks of tasks/README.md for one variant. */
public final class VariantValidator {

    static final long MAX_REFERENCE_MS = 10_000;
    static final int STATEMENT_MIN = 400;
    static final int STATEMENT_MAX = 3000;
    static final int STARTER_LOC_MIN = 40;
    static final int STARTER_LOC_MAX = 400;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final SchemaCheck schema;
    private final SandboxExecutor sandbox;
    private final ExecutorService pool;
    private final Integer runsOverride;
    private final Clock clock;
    private final ForbiddenConstructCheck forbidden = new ForbiddenConstructCheck();

    public VariantValidator(SchemaCheck schema, SandboxExecutor sandbox, ExecutorService pool, Integer runsOverride,
                            Clock clock) {
        this.schema = schema;
        this.sandbox = sandbox;
        this.pool = pool;
        this.runsOverride = runsOverride;
        this.clock = clock;
    }

    public ValidationReport validate(Path templateDir, Path variantDir) {
        List<Check> checks = new ArrayList<>();
        VariantSources sources = VariantSources.read(variantDir);
        String hash = ContentHash.of(sources.allFiles());
        String expectedCode = templateCodeOf(templateDir) + "-" + variantDir.getFileName();

        Optional<VariantSpec> spec = checkSchema(templateDir, variantDir, expectedCode, checks);
        if (spec.isEmpty() || !checkFiles(spec.get(), sources, checks)) {
            return report(spec.map(VariantSpec::code).orElse(expectedCode), hash, checks, null);
        }
        VariantSpec variant = spec.get();
        checks.add(checkForbidden(sources));
        checks.add(checkSizes(sources));
        checks.add(LeakCheck.findLeak(sources.starter(), sources.solution(), sources.visibleTests())
                .map(leak -> new Check("no_leak", false, "Видимые тесты содержат фрагмент решения: " + leak))
                .orElse(new Check("no_leak", true, "Фрагментов решения в видимых тестах нет")));

        ValidationReport.Runs runs = runSandboxChecks(variant, sources, checks);
        checks.add(new Check("content_hash", true, hash));
        return report(variant.code(), hash, checks, runs);
    }

    // 1. schema --------------------------------------------------------------------------------

    private Optional<VariantSpec> checkSchema(Path templateDir, Path variantDir, String expectedCode, List<Check> checks) {
        Path templateFile = templateDir.resolve("template.yaml");
        Path taskFile = variantDir.resolve("task.yaml");
        if (!Files.isRegularFile(templateFile) || !Files.isRegularFile(taskFile)) {
            checks.add(new Check("schema", false, "Нет template.yaml или task.yaml"));
            return Optional.empty();
        }
        JsonNode templateNode;
        JsonNode taskNode;
        try {
            templateNode = SchemaCheck.readYaml(templateFile);
            taskNode = SchemaCheck.readYaml(taskFile);
        } catch (RuntimeException e) {
            checks.add(new Check("schema", false, "YAML не читается: " + TaskBankCli.rootMessage(e)));
            return Optional.empty();
        }
        List<String> problems = new ArrayList<>();
        addIfPresent(problems, "template.yaml", schema.validateTemplate(templateNode));
        addIfPresent(problems, "task.yaml", schema.validateTask(taskNode));
        if (!problems.isEmpty()) {
            checks.add(new Check("schema", false, String.join("; ", problems)));
            return Optional.empty();
        }
        TemplateSpec template = MAPPER.convertValue(templateNode, TemplateSpec.class);
        VariantSpec variant = MAPPER.convertValue(taskNode, VariantSpec.class);
        if (!templateDir.getFileName().toString().startsWith(template.code() + "-")) {
            problems.add("каталог шаблона " + templateDir.getFileName() + " должен начинаться с " + template.code() + "-");
        }
        if (!variant.template().equals(template.code())) {
            problems.add("task.yaml: template = " + variant.template() + ", ожидается " + template.code());
        }
        if (!variant.code().equals(expectedCode)) {
            problems.add("task.yaml: code = " + variant.code() + ", ожидается " + expectedCode);
        }
        if (variant.flakyPolicy().referencePassMin() > variant.flakyPolicy().runs()
                || variant.flakyPolicy().starterFailMin() > variant.flakyPolicy().runs()) {
            problems.add("flaky_policy: минимумы не могут превышать runs");
        }
        checks.add(new Check("schema", problems.isEmpty(),
                problems.isEmpty() ? "template.yaml и task.yaml соответствуют схеме" : String.join("; ", problems)));
        return problems.isEmpty() ? Optional.of(variant) : Optional.empty();
    }

    // 2. files ---------------------------------------------------------------------------------

    private boolean checkFiles(VariantSpec variant, VariantSources sources, List<Check> checks) {
        List<String> problems = new ArrayList<>();
        Set<String> editable = new HashSet<>(variant.editable());
        Set<String> readonly = new HashSet<>(variant.readonly());
        Set<String> overlap = new HashSet<>(editable);
        overlap.retainAll(readonly);
        if (!overlap.isEmpty()) {
            problems.add("файлы одновременно editable и readonly: " + overlap);
        }
        Set<String> declared = new HashSet<>(editable);
        declared.addAll(readonly);
        if (!declared.equals(sources.starter().keySet())) {
            problems.add("starter/ должен содержать ровно editable ∪ readonly; в starter: " + sources.starter().keySet()
                    + ", в task.yaml: " + declared);
        }
        if (!variant.isCalibration() && sources.solution().isEmpty()) {
            problems.add("нет solution/");
        }
        if (!editable.containsAll(sources.solution().keySet())) {
            problems.add("solution/ может содержать только editable-файлы: " + sources.solution().keySet());
        }
        if (sources.statement() == null) {
            problems.add("нет statement.md");
        }
        for (var tests : List.of(sources.visibleTests(), sources.hiddenTests())) {
            tests.keySet().stream().filter(p -> !p.startsWith("src/test/java/"))
                    .forEach(p -> problems.add("тест вне src/test/java: " + p));
        }
        try {
            SourceArchive.build(sources.solvedFiles());
            if (!sources.visibleTests().isEmpty()) {
                SourceArchive.build(sources.visibleTestFiles());
            }
            if (!sources.hiddenTests().isEmpty()) {
                SourceArchive.build(sources.hiddenTestFiles());
            }
        } catch (InvalidSourceException e) {
            problems.add(e.getMessage());
        }
        int visible = ExpectedTests.of(sources.visibleTestFiles()).methods().size();
        int hidden = ExpectedTests.of(sources.hiddenTestFiles()).methods().size();
        if (variant.isCalibration()) {
            if (visible < 1) {
                problems.add("калибровочному блоку нужен хотя бы один видимый тест");
            }
        } else {
            if (visible < 2 || visible > 5) {
                problems.add("видимых тестов " + visible + ", нужно 2–5");
            }
            if (hidden < 5 || hidden > 15) {
                problems.add("скрытых тестов " + hidden + ", нужно 5–15");
            }
        }
        boolean ok = problems.isEmpty();
        checks.add(new Check("files", ok, ok ? "видимых тестов: " + visible + ", скрытых: " + hidden : String.join("; ", problems)));
        return ok;
    }

    // 7-8. static checks ------------------------------------------------------------------------

    private Check checkForbidden(VariantSources sources) {
        List<String> violations = new ArrayList<>();
        sources.starter().forEach((path, code) -> violations.addAll(forbidden.check("starter/" + path, code, false)));
        sources.solution().forEach((path, code) -> violations.addAll(forbidden.check("solution/" + path, code, false)));
        sources.visibleTests().forEach((path, code) -> violations.addAll(forbidden.check("tests-visible/" + path, code, true)));
        sources.hiddenTests().forEach((path, code) -> violations.addAll(forbidden.check("tests-hidden/" + path, code, true)));
        return violations.isEmpty()
                ? new Check("forbidden", true, "Запрещённых конструкций нет")
                : new Check("forbidden", false, String.join("; ", violations));
    }

    private Check checkSizes(VariantSources sources) {
        int statementLength = sources.statement().strip().length();
        long loc = sources.starter().values().stream()
                .flatMap(String::lines).filter(line -> !line.isBlank()).count();
        List<String> problems = new ArrayList<>();
        if (statementLength < STATEMENT_MIN || statementLength > STATEMENT_MAX) {
            problems.add("statement.md: " + statementLength + " символов, нужно " + STATEMENT_MIN + "–" + STATEMENT_MAX);
        }
        if (loc < STARTER_LOC_MIN || loc > STARTER_LOC_MAX) {
            problems.add("starter: " + loc + " непустых строк, нужно " + STARTER_LOC_MIN + "–" + STARTER_LOC_MAX);
        }
        return new Check("sizes", problems.isEmpty(),
                problems.isEmpty() ? "условие " + statementLength + " символов, starter " + loc + " строк"
                        : String.join("; ", problems));
    }

    // 3-6. sandbox checks ------------------------------------------------------------------------

    private ValidationReport.Runs runSandboxChecks(VariantSpec variant, VariantSources sources, List<Check> checks) {
        int runs = runsOverride != null ? runsOverride : variant.flakyPolicy().runs();
        int referenceMin = Math.min(variant.flakyPolicy().referencePassMin(), runs);
        int starterMin = Math.min(variant.flakyPolicy().starterFailMin(), runs);

        List<SourceFile> starterVisible = concat(sources.starterFiles(), sources.visibleTestFiles());
        List<SourceFile> reference = concat(sources.solvedFiles(), sources.visibleTestFiles(), sources.hiddenTestFiles());
        List<SourceFile> starterHidden = concat(sources.starterFiles(), sources.hiddenTestFiles());
        ExpectedTests referenceTests = ExpectedTests.of(concat(sources.visibleTestFiles(), sources.hiddenTestFiles()));
        ExpectedTests hiddenTests = ExpectedTests.of(sources.hiddenTestFiles());
        boolean calibration = variant.isCalibration();

        Future<SandboxRun> compileRun = pool.submit(() -> sandbox.run(SourceArchive.build(starterVisible)));
        List<Future<SandboxRun>> referenceRuns = new ArrayList<>();
        List<Future<SandboxRun>> starterRuns = new ArrayList<>();
        byte[] referenceArchive = SourceArchive.build(reference);
        byte[] starterArchive = calibration ? null : SourceArchive.build(starterHidden);
        for (int i = 0; i < runs; i++) {
            referenceRuns.add(pool.submit(() -> sandbox.run(referenceArchive)));
            if (!calibration) {
                starterRuns.add(pool.submit(() -> sandbox.run(starterArchive)));
            }
        }

        ParsedRun compiled = SandboxOutputParser.parse(await(compileRun));
        checks.add(compiled.compiled()
                ? new Check("starter_compiles", true, "starter и видимые тесты компилируются")
                : new Check("starter_compiles", false, "Ошибка компиляции: " + abbreviate(compiled.compileOutput())));

        int referencePassed = 0;
        long referenceMaxMs = 0;
        String firstReferenceProblem = null;
        for (Future<SandboxRun> future : referenceRuns) {
            SandboxRun run = await(future);
            referenceMaxMs = Math.max(referenceMaxMs, run.durationMs());
            Optional<String> problem = referenceProblem(run, referenceTests);
            if (problem.isEmpty()) {
                referencePassed++;
            } else if (firstReferenceProblem == null) {
                firstReferenceProblem = problem.get();
            }
        }
        checks.add(new Check("reference_passes", referencePassed >= referenceMin,
                "решение прошло все тесты в " + referencePassed + " из " + runs + " прогонов (нужно не менее "
                        + referenceMin + ")" + (firstReferenceProblem == null ? "" : "; " + firstReferenceProblem)));

        int starterFailed = 0;
        if (calibration) {
            checks.add(new Check("starter_fails", true, "не требуется для калибровочного блока"));
        } else {
            for (Future<SandboxRun> future : starterRuns) {
                if (starterFailsHidden(await(future), hiddenTests)) {
                    starterFailed++;
                }
            }
            checks.add(new Check("starter_fails", starterFailed >= starterMin,
                    "starter провалил скрытые тесты в " + starterFailed + " из " + runs + " прогонов (нужно не менее "
                            + starterMin + ")"));
        }

        checks.add(new Check("reference_time", referenceMaxMs <= MAX_REFERENCE_MS,
                "самый долгий прогон решения — " + referenceMaxMs + " мс (не более " + MAX_REFERENCE_MS + ")"));
        return new ValidationReport.Runs(runs, referencePassed, starterFailed, referenceMaxMs);
    }

    private static Optional<String> referenceProblem(SandboxRun run, ExpectedTests expected) {
        if (run.timedOut()) {
            return Optional.of("прогон превысил таймаут");
        }
        ParsedRun parsed = SandboxOutputParser.parse(run);
        if (!parsed.compiled()) {
            return Optional.of("решение не компилируется: " + abbreviate(parsed.compileOutput()));
        }
        if (!parsed.reportPresent() || !expected.matches(parsed.testCases())) {
            return Optional.of("отчёт JUnit отсутствует или не совпадает с тестами задачи");
        }
        return parsed.testCases().stream().filter(c -> !c.passed()).findFirst()
                .map(c -> "не прошёл " + c.className() + "." + c.name() + ": " + abbreviate(c.message()));
    }

    private static boolean starterFailsHidden(SandboxRun run, ExpectedTests hidden) {
        if (run.timedOut()) {
            return true;  // a hanging defect (e.g. deadlock) is a legitimate failure
        }
        ParsedRun parsed = SandboxOutputParser.parse(run);
        return parsed.compiled() && parsed.reportPresent() && hidden.matches(parsed.testCases())
                && parsed.testCases().stream().anyMatch(c -> c.status() != TestCaseResult.Status.PASSED);
    }

    // helpers ------------------------------------------------------------------------------------

    private ValidationReport report(String code, String hash, List<Check> checks, ValidationReport.Runs runs) {
        boolean passed = !checks.isEmpty() && checks.stream().allMatch(Check::passed);
        return new ValidationReport(code, passed ? ValidationReport.Status.PASSED : ValidationReport.Status.FAILED,
                hash, clock.instant(), ValidationReport.VALIDATOR_VERSION, runs, List.copyOf(checks));
    }

    private static String templateCodeOf(Path templateDir) {
        String name = templateDir.getFileName().toString();
        int dash = name.indexOf('-');
        return dash < 0 ? name : name.substring(0, dash);
    }

    @SafeVarargs
    private static List<SourceFile> concat(List<SourceFile>... lists) {
        List<SourceFile> all = new ArrayList<>();
        for (List<SourceFile> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    private static SandboxRun await(Future<SandboxRun> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Sandbox run failed", e.getCause());
        }
    }

    private static void addIfPresent(List<String> problems, String file, String description) {
        if (!description.isEmpty()) {
            problems.add(file + ": " + description);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String single = text.replaceAll("\\s+", " ").strip();
        return single.length() <= 300 ? single : single.substring(0, 300) + "…";
    }

    /** Parsed task.yaml of a variant; used by the loader and stats commands. */
    public static VariantSpec readSpec(Path variantDir) {
        return MAPPER.convertValue(SchemaCheck.readYaml(variantDir.resolve("task.yaml")), VariantSpec.class);
    }

    public static TemplateSpec readTemplate(Path templateDir) {
        return MAPPER.convertValue(SchemaCheck.readYaml(templateDir.resolve("template.yaml")), TemplateSpec.class);
    }
}
