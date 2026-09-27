package ru.gits.taskbank;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.gits.core.GitsVersion;
import ru.gits.sandbox.SandboxConfig;
import ru.gits.sandbox.SandboxExecutor;
import ru.gits.taskbank.TaskBankLayout.VariantLocation;
import ru.gits.taskbank.check.SchemaCheck;

/** Task bank tool: {@code validate} and {@code verify-hashes}; {@code load}, {@code stats} follow in P06/P24. */
public final class TaskBankCli {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILED = 1;
    static final int EXIT_USAGE = 64;

    private TaskBankCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream out) {
        if (args.length == 0 || "help".equals(args[0])) {
            printUsage(out);
            return args.length == 0 ? EXIT_USAGE : EXIT_OK;
        }
        try {
            Options options = Options.parse(args);
            return switch (options.command()) {
                case "validate" -> validate(options, out);
                case "verify-hashes" -> verifyHashes(options, out);
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
        out.println("                         [--image gits-sandbox-java:local] [--runtime runc]");
        out.println("  gits-taskbank verify-hashes <tasks/java> [--variant ...]");
        out.println("  gits-taskbank help");
    }

    record Options(String command, Path root, Optional<String> variant, Optional<Integer> runs, int parallel,
                   String image, String runtime) {

        static Options parse(String[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Не указан каталог банка задач");
            }
            String variant = null;
            Integer runs = null;
            int parallel = 4;
            String image = "gits-sandbox-java:local";
            String runtime = "runc";
            List<String> rest = new ArrayList<>(List.of(args).subList(2, args.length));
            for (int i = 0; i < rest.size(); i++) {
                String option = rest.get(i);
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
                    default -> throw new IllegalArgumentException("Неизвестный параметр: " + option);
                }
            }
            return new Options(args[0], Path.of(args[1]), Optional.ofNullable(variant), Optional.ofNullable(runs),
                    parallel, image, runtime);
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
