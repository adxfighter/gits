package ru.gits.taskbank;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.gits.core.GitsVersion;
import ru.gits.sandbox.SandboxConfig;
import ru.gits.sandbox.SandboxExecutor;
import ru.gits.taskbank.TaskBankLayout.VariantLocation;
import ru.gits.taskbank.check.SchemaCheck;
import ru.gits.taskbank.load.LoadCommand;
import ru.gits.taskbank.load.TaskBankLoader;

/** Task bank tool: {@code validate}, {@code verify-hashes}, {@code stats} and {@code load}. */
public final class TaskBankCli {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILED = 1;
    static final int EXIT_USAGE = 64;
    /** The command could not run at all (no database, bank directory missing), as opposed to skipped variants. */
    static final int EXIT_ERROR = 2;
    /**
     * Logging config of the CLI only: it is not named logback.xml, because gits-api has this jar on its classpath
     * and must keep its own logging.
     */
    public static final String CLI_LOGGING_CONFIG = "logback-taskbank-cli.xml";

    private TaskBankCli() {
    }

    public static void main(String[] args) {
        System.setProperty("logback.configurationFile", CLI_LOGGING_CONFIG);
        System.exit(run(args, System.out));
    }

    /** Runs a command and returns its exit code; used by {@link #main} and by tests that embed the CLI. */
    public static int run(String[] args, PrintStream out) {
        if (args.length == 0 || "help".equals(args[0])) {
            printUsage(out);
            return args.length == 0 ? EXIT_USAGE : EXIT_OK;
        }
        try {
            Options options = Options.parse(args);
            return switch (options.command()) {
                case "validate" -> validate(options, out);
                case "verify-hashes" -> verifyHashes(options, out);
                case "stats" -> stats(options, out);
                case "load" -> load(options, out);
                case "demo-seed" -> demoSeed(options, out);
                default -> {
                    out.println("Unknown command: " + options.command());
                    printUsage(out);
                    yield EXIT_USAGE;
                }
            };
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            printUsage(out);
            return EXIT_USAGE;
        }
    }

    private static int validate(Options options, PrintStream out) {
        var layout = new TaskBankLayout(options.root());
        if (options.schemaOnly()) {
            return validateSchemas(layout, options, out);
        }
        List<VariantLocation> variants = selected(layout, options.variant());
        if (variants.isEmpty()) {
            out.println("Варианты не найдены: " + options.root() + options.variant().map(v -> " / " + v).orElse(""));
            return EXIT_FAILED;
        }
        var sandbox = new SandboxExecutor(new SandboxConfig("docker", options.image(), options.runtime(),
                Duration.ofSeconds(30), 2 * 1024 * 1024));
        ExecutorService pool = Executors.newFixedThreadPool(options.parallel());
        int failed = 0;
        try {
            var validator = new VariantValidator(new SchemaCheck(layout.schemaDirectory()), sandbox, pool,
                    options.runs().orElse(null), Clock.systemUTC());
            for (VariantLocation location : variants) {
                long started = System.nanoTime();
                ValidationReport report = validator.validate(location.templateDir(), location.variantDir());
                ReportFiles.write(location.variantDir(), report);
                long seconds = Duration.ofNanos(System.nanoTime() - started).toSeconds();
                out.printf("%-6s %s (%d с)%n", report.status(), report.code(), seconds);
                if (report.status() == ValidationReport.Status.FAILED) {
                    failed++;
                    report.checks().stream().filter(c -> !c.passed())
                            .forEach(c -> out.println("       ✗ " + c.id() + ": " + c.details()));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        out.printf("Итого: %d вариантов, не прошли %d%n", variants.size(), failed);
        return failed == 0 ? EXIT_OK : EXIT_FAILED;
    }

    /**
     * Schema check without the sandbox: template.yaml of every selected template (even without variants)
     * and task.yaml of every selected variant. {@code --variant} narrows both to that template/variant.
     */
    private static int validateSchemas(TaskBankLayout layout, Options options, PrintStream out) {
        var schema = new SchemaCheck(layout.schemaDirectory());
        int problems = 0;
        int checked = 0;
        for (Path templateDir : layout.templates()) {
            String code = templateDir.getFileName().toString().split("-", 2)[0];
            if (options.variant().isPresent() && !options.variant().get().startsWith(code)) {
                continue;
            }
            checked++;
            Path file = templateDir.resolve("template.yaml");
            String result;
            try {
                var node = SchemaCheck.readYaml(file);
                result = schema.validateTemplate(node);
                String templateCode = node.path("code").asText();
                if (!templateDir.getFileName().toString().startsWith(templateCode + "-")) {
                    result = (result.isEmpty() ? "" : result + "; ") + "каталог должен начинаться с " + templateCode + "-";
                }
            } catch (RuntimeException e) {
                result = "не читается как YAML: " + rootMessage(e);
            }
            if (!result.isEmpty()) {
                problems++;
                out.println("✗ " + templateDir.getFileName() + "/template.yaml: " + result);
            }
        }
        for (VariantLocation location : selected(layout, options.variant())) {
            checked++;
            String result;
            try {
                result = schema.validateTask(SchemaCheck.readYaml(location.variantDir().resolve("task.yaml")));
            } catch (RuntimeException e) {
                result = "не читается как YAML: " + rootMessage(e);
            }
            if (!result.isEmpty()) {
                problems++;
                out.println("✗ " + location.code() + "/task.yaml: " + result);
            }
        }
        out.printf("schema-only: проверено файлов %d, с ошибками %d%n", checked, problems);
        if (checked == 0) {
            out.println("Ничего не найдено" + options.variant().map(v -> " для " + v).orElse(""));
            return EXIT_FAILED;
        }
        return problems == 0 ? EXIT_OK : EXIT_FAILED;
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.lines().findFirst().orElse(message);
    }

    private static int verifyHashes(Options options, PrintStream out) {
        var layout = new TaskBankLayout(options.root());
        List<VariantLocation> variants = selected(layout, options.variant());
        int problems = 0;
        for (VariantLocation location : variants) {
            Optional<String> problem = ReportFiles.problemWithStoredReport(location.variantDir());
            if (problem.isPresent()) {
                problems++;
                out.println("✗ " + location.code() + ": " + problem.get());
            }
        }
        out.printf("verify-hashes: %d вариантов, проблем %d%n", variants.size(), problems);
        return problems == 0 ? EXIT_OK : EXIT_FAILED;
    }

    /** Fails on unreadable task files and on REVIEW.md problems: the catalog must match the files exactly. */
    private static int stats(Options options, PrintStream out) {
        BankStats stats;
        try {
            stats = BankStats.collect(options.root());
        } catch (RuntimeException e) {
            out.println("Банк задач не читается: " + rootMessage(e) + " — запустите validate --schema-only");
            return EXIT_FAILED;
        }
        out.print(stats.summary());
        int result = stats.reviewProblems().isEmpty() ? EXIT_OK : EXIT_FAILED;
        if (options.catalog().isEmpty()) {
            return result;
        }
        Path catalog = options.catalog().get();
        if (options.check()) {
            boolean current = stats.isCatalogCurrent(catalog);
            if (!Files.isRegularFile(catalog)) {
                out.println("Каталог не найден: " + catalog + " — выполните stats с --catalog без --check");
            } else {
                out.println(current ? "Каталог актуален: " + catalog
                        : "Каталог устарел: " + catalog + " — выполните stats с --catalog без --check");
            }
            return current ? result : EXIT_FAILED;
        }
        out.println((stats.writeCatalog(catalog) ? "Каталог обновлён: " : "Каталог не изменился: ") + catalog);
        return result;
    }

    /** EXIT_OK — everything loaded, EXIT_FAILED — some variants skipped, EXIT_ERROR — nothing could be loaded. */
    private static int load(Options options, PrintStream out) {
        try {
            var summary = LoadCommand.run(options.root(), Set.copyOf(options.exclude()), out);
            return summary.skipped() == 0 ? EXIT_OK : EXIT_FAILED;
        } catch (RuntimeException e) {
            out.println("Загрузка не выполнена: " + rootMessage(e));
            return EXIT_ERROR;
        }
    }

    /** EXIT_OK — every file imported or already there, EXIT_FAILED — some skipped, EXIT_ERROR — nothing ran. */
    private static int demoSeed(Options options, PrintStream out) {
        String employer = options.employer().orElse(System.getenv("DEMO_EMPLOYER_EMAIL"));
        if (employer == null || employer.isBlank()) {
            out.println("Не указан работодатель: --employer или DEMO_EMPLOYER_EMAIL");
            return EXIT_USAGE;
        }
        try {
            var summary = ru.gits.taskbank.demo.DemoSeedCommand.run(options.root(), employer.strip(), out);
            boolean onlyPresent = summary.messages().stream().filter(m -> m.contains("пропущена"))
                    .allMatch(m -> m.contains("уже есть"));
            return onlyPresent ? EXIT_OK : EXIT_FAILED;
        } catch (RuntimeException e) {
            out.println("Демо-сессии не загружены: " + rootMessage(e));
            return EXIT_ERROR;
        }
    }

    private static List<VariantLocation> selected(TaskBankLayout layout, Optional<String> variant) {
        return layout.variants().stream()
                .filter(v -> variant.isEmpty() || v.code().equals(variant.get())
                        || v.code().startsWith(variant.get() + "-"))
                .toList();
    }

    private static void printUsage(PrintStream out) {
        out.println(GitsVersion.display() + " task bank");
        out.println("Usage:");
        out.println("  gits-taskbank validate <tasks/java> [--variant T01-v03 | --variant T01] [--runs N] [--parallel N]");
        out.println("                         [--schema-only]  (only template.yaml/task.yaml, no sandbox)");
        out.println("                         [--image gits-sandbox-java:local] [--runtime runc]");
        out.println("  gits-taskbank verify-hashes <tasks/java> [--variant ...]");
        out.println("  gits-taskbank stats <tasks/java> [--catalog tasks/java/CATALOG.md [--check]]");
        out.println("                         (--check: fail if the catalog is out of date, do not write it)");
        out.println("  gits-taskbank load <tasks/java> [--exclude T00,...]  (database from SPRING_DATASOURCE_*;");
        out.println("                         the format example T00 is excluded by default, --exclude '' loads it)");
        out.println("  gits-taskbank demo-seed <seed/demo-sessions> [--employer employer@demo.local]");
        out.println("                         (recorded sessions into the employer's company; default employer —");
        out.println("                         DEMO_EMPLOYER_EMAIL; sessions already there are skipped)");
        out.println("  gits-taskbank help");
    }

    record Options(String command, Path root, Optional<String> variant, Optional<Integer> runs, int parallel,
                   String image, String runtime, boolean schemaOnly, Optional<Path> catalog, boolean check,
                   List<String> exclude, Optional<String> employer) {

        static Options parse(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("demo-seed".equals(args[0]) ? "Не указан каталог демо-сессий"
                        : "Не указан каталог банка задач");
            }
            String variant = null;
            Integer runs = null;
            int parallel = 4;
            String image = "gits-sandbox-java:local";
            String runtime = "runc";
            boolean schemaOnly = false;
            Path catalog = null;
            boolean check = false;
            List<String> exclude = List.of(TaskBankLoader.EXAMPLE_TEMPLATE);
            String employer = null;
            List<String> rest = new ArrayList<>(List.of(args).subList(2, args.length));
            for (int i = 0; i < rest.size(); i++) {
                String option = rest.get(i);
                if (option.equals("--schema-only")) {
                    schemaOnly = true;
                    continue;
                }
                if (option.equals("--check")) {
                    check = true;
                    continue;
                }
                if (i + 1 >= rest.size()) {
                    throw new IllegalArgumentException("Нет значения для " + option);
                }
                String value = rest.get(++i);
                switch (option) {
                    case "--variant" -> variant = value;
                    case "--runs" -> runs = positive(option, value);
                    case "--parallel" -> parallel = positive(option, value);
                    case "--image" -> image = value;
                    case "--runtime" -> runtime = value;
                    case "--catalog" -> catalog = Path.of(value);
                    case "--employer" -> employer = value;
                    case "--exclude" -> exclude = List.of(value.split(",")).stream()
                            .map(String::strip).filter(code -> !code.isEmpty()).toList();
                    default -> throw new IllegalArgumentException("Неизвестный параметр: " + option);
                }
            }
            if (check && catalog == null) {
                throw new IllegalArgumentException("--check используется вместе с --catalog");
            }
            boolean statsCommand = "stats".equals(args[0]);
            if (!statsCommand && catalog != null) {
                throw new IllegalArgumentException("--catalog и --check применимы только к stats");
            }
            if (statsCommand && variant != null) {
                throw new IllegalArgumentException("stats описывает весь банк, --variant не применим");
            }
            return new Options(args[0], Path.of(args[1]), Optional.ofNullable(variant), Optional.ofNullable(runs),
                    parallel, image, runtime, schemaOnly, Optional.ofNullable(catalog), check, exclude,
                    Optional.ofNullable(employer));
        }

        private static int positive(String option, String value) {
            try {
                int number = Integer.parseInt(value);
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // fall through
            }
            throw new IllegalArgumentException(option + " должен быть положительным числом: " + value);
        }
    }
}
