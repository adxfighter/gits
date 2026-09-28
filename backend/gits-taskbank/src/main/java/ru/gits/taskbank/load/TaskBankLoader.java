package ru.gits.taskbank.load;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.common.Level;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplate;
import ru.gits.core.task.TaskTemplateRepository;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.task.VariantStatus;
import ru.gits.taskbank.ContentHash;
import ru.gits.taskbank.ReportFiles;
import ru.gits.taskbank.TaskBankLayout;
import ru.gits.taskbank.TaskBankLayout.VariantLocation;
import ru.gits.taskbank.TaskSpecs.TemplateSpec;
import ru.gits.taskbank.TaskSpecs.VariantSpec;
import ru.gits.taskbank.ValidationReport;
import ru.gits.taskbank.VariantSources;
import ru.gits.taskbank.VariantValidator;
import ru.gits.taskbank.check.SchemaCheck;

/**
 * Loads validated variants of a task bank directory (tasks/java) into the database.
 *
 * <ul>
 *   <li>Only variants whose validation.json is PASSED and whose content hash matches the files are loaded; any other
 *       variant, or one that cannot be read, is skipped with a warning and keeps its state in the database.</li>
 *   <li>Idempotent by code + content hash: an unchanged variant is not written, a changed one is replaced and
 *       re-enabled.</li>
 *   <li>A variant that is no longer in the bank (or belongs to an excluded template) is marked DISABLED, never
 *       deleted: sessions may reference it. Variants of a template directory that lost or broke its template.yaml
 *       count as skipped, not as removed; an empty bank is an error, so a wrong mount cannot disable everything.</li>
 *   <li>Template and variant with its files are written in one transaction per variant.</li>
 * </ul>
 * template.yaml is not part of the variant content hash; it is checked against the schema when loaded.
 */
public final class TaskBankLoader {

    /** Format example, never offered to candidates. */
    public static final String EXAMPLE_TEMPLATE = "T00";

    private static final Logger LOG = LoggerFactory.getLogger(TaskBankLoader.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Result of one load; {@code warnings} explain every skipped variant. */
    public record Summary(int loaded, int updated, int unchanged, int skipped, int disabled, List<String> warnings) {

        @Override
        public String toString() {
            return "загружено " + loaded + ", обновлено " + updated + ", без изменений " + unchanged
                    + ", пропущено " + skipped + ", отключено " + disabled;
        }
    }

    private enum Outcome { LOADED, UPDATED, UNCHANGED }

    /** A variant read once from disk: the stored files are exactly the ones whose hash was checked. */
    private record Checked(VariantSpec spec, VariantSources sources, String hash, String report) {
    }

    private final TaskTemplateRepository templates;
    private final TaskVariantRepository variants;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Set<String> excludedTemplates;

    public TaskBankLoader(TaskTemplateRepository templates, TaskVariantRepository variants,
                          PlatformTransactionManager transactionManager, Clock clock, Set<String> excludedTemplates) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.variants = Objects.requireNonNull(variants, "variants");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.excludedTemplates = Set.copyOf(excludedTemplates);
    }

    /**
     * Loads every variant under {@code bankRoot} (tasks/java).
     *
     * @throws IllegalStateException when the directory holds no variants at all (most likely a wrong mount)
     */
    public Summary load(Path bankRoot) {
        var layout = new TaskBankLayout(bankRoot);
        var schema = new SchemaCheck(layout.schemaDirectory());
        List<String> warnings = new ArrayList<>();
        Set<String> present = new HashSet<>();
        int[] counts = new int[4];  // loaded, updated, unchanged, skipped
        List<VariantLocation> locations = layout.variants();
        counts[3] += keepVariantsOfBrokenTemplates(bankRoot, present, warnings);
        if (locations.isEmpty() && present.isEmpty()) {
            throw new IllegalStateException("В банке задач нет ни одного варианта: " + bankRoot
                    + " — проверьте путь и монтирование каталога");
        }
        Map<Path, Optional<TemplateSpec>> templateSpecs = new HashMap<>();
        for (VariantLocation location : locations) {
            Optional<TemplateSpec> template = templateSpecs.computeIfAbsent(location.templateDir(),
                    dir -> readTemplate(dir, schema, warnings));
            if (template.isPresent() && excludedTemplates.contains(template.get().code())) {
                continue;
            }
            present.add(location.code());
            if (template.isEmpty()) {
                counts[3]++;
                continue;
            }
            Optional<Checked> checked = check(location, warnings);
            if (checked.isEmpty()) {
                counts[3]++;
                continue;
            }
            try {
                Outcome outcome = transactions.execute(status -> store(template.get(), checked.get()));
                counts[Objects.requireNonNull(outcome).ordinal()]++;
            } catch (RuntimeException e) {
                counts[3]++;
                warn(warnings, location.code() + ": не загружен — " + e.getMessage());
            }
        }
        int disabled = Objects.requireNonNull(transactions.execute(status -> disableMissing(present)));
        var summary = new Summary(counts[0], counts[1], counts[2], counts[3], disabled, List.copyOf(warnings));
        LOG.info("Банк задач {}: {}", bankRoot, summary);
        return summary;
    }

    /** Variant directories of templates without template.yaml: they stay as they are in the database. */
    private static int keepVariantsOfBrokenTemplates(Path bankRoot, Set<String> present, List<String> warnings) {
        int skipped = 0;
        try (Stream<Path> children = Files.list(bankRoot)) {
            for (Path templateDir : children.filter(Files::isDirectory).sorted().toList()) {
                Path variantsDir = templateDir.resolve("variants");
                if (Files.isRegularFile(templateDir.resolve("template.yaml")) || !Files.isDirectory(variantsDir)) {
                    continue;
                }
                String templateCode = templateDir.getFileName().toString().split("-", 2)[0];
                try (Stream<Path> variantDirs = Files.list(variantsDir)) {
                    for (Path variantDir : variantDirs.filter(Files::isDirectory).sorted().toList()) {
                        String code = templateCode + "-" + variantDir.getFileName();
                        present.add(code);
                        skipped++;
                        warn(warnings, code + ": пропущен — у шаблона " + templateDir.getFileName() + " нет template.yaml");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + bankRoot, e);
        }
        return skipped;
    }

    private static Optional<TemplateSpec> readTemplate(Path templateDir, SchemaCheck schema, List<String> warnings) {
        try {
            String problems = schema.validateTemplate(SchemaCheck.readYaml(templateDir.resolve("template.yaml")));
            if (!problems.isEmpty()) {
                warn(warnings, templateDir.getFileName() + ": template.yaml не соответствует схеме — " + problems
                        + "; варианты шаблона пропущены");
                return Optional.empty();
            }
            return Optional.of(VariantValidator.readTemplate(templateDir));
        } catch (RuntimeException e) {
            warn(warnings, templateDir.getFileName() + ": template.yaml не читается — " + e.getMessage()
                    + "; варианты шаблона пропущены");
            return Optional.empty();
        }
    }

    /** Reads the variant once and checks its report and hash against exactly these files. */
    private static Optional<Checked> check(VariantLocation location, List<String> warnings) {
        try {
            Optional<ValidationReport> report = ReportFiles.read(location.variantDir());
            if (report.isEmpty()) {
                warn(warnings, location.code() + ": пропущен — нет validation.json, запустите validate");
                return Optional.empty();
            }
            if (report.get().status() != ValidationReport.Status.PASSED) {
                warn(warnings, location.code() + ": пропущен — последняя валидация не пройдена; в БД остаётся "
                        + "прежняя проверенная версия, если она была");
                return Optional.empty();
            }
            VariantSources sources = VariantSources.read(location.variantDir());
            String hash = ContentHash.of(sources.allFiles());
            if (!hash.equals(report.get().contentHash())) {
                warn(warnings, location.code() + ": пропущен — файлы изменены после валидации (content_hash не совпадает)");
                return Optional.empty();
            }
            VariantSpec spec = VariantValidator.readSpec(location.variantDir());
            return Optional.of(new Checked(spec, sources, hash, readReport(location.variantDir())));
        } catch (RuntimeException e) {
            warn(warnings, location.code() + ": пропущен — файлы варианта не читаются: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Outcome store(TemplateSpec templateSpec, Checked checked) {
        Instant now = clock.instant();
        TaskTemplate template = upsertTemplate(templateSpec, now);
        VariantSpec spec = checked.spec();
        TaskKind kind = spec.isCalibration() ? TaskKind.CALIBRATION : TaskKind.TASK;
        Level level = level(spec.level());
        String params = json(spec.difficultyParams() == null ? Map.of() : spec.difficultyParams());

        Optional<TaskVariant> existing = variants.findByCode(spec.code());
        if (existing.isPresent() && existing.get().getContentHash().equals(checked.hash())
                && existing.get().getStatus() == VariantStatus.VALIDATED
                && existing.get().getTemplate().getId().equals(template.getId())) {
            // the same content, validated again (a newer validator writes more, e.g. the tests the starter passes)
            if (existing.get().getValidationReport() == null
                    || !sameJson(existing.get().getValidationReport(), checked.report())) {
                existing.get().refreshValidationReport(checked.report(), now);
                return Outcome.UPDATED;
            }
            return Outcome.UNCHANGED;
        }
        TaskVariant variant;
        Outcome outcome;
        if (existing.isPresent()) {
            variant = existing.get();
            variant.replaceContent(template, kind, level, spec.domain(), params, checked.sources().statement(),
                    spec.timeLimitMin(), checked.hash(), checked.report(), now);
            variant.clearFiles();
            variants.flush();
            outcome = Outcome.UPDATED;
        } else {
            variant = new TaskVariant(template, spec.code(), kind, level, spec.domain(), params,
                    checked.sources().statement(), spec.timeLimitMin(), checked.hash(), checked.report(), now);
            outcome = Outcome.LOADED;
        }
        addFiles(variant, spec, checked.sources());
        variants.save(variant);
        return outcome;
    }

    private TaskTemplate upsertTemplate(TemplateSpec spec, Instant now) {
        String competencies = json(spec.competencies());
        String difficultyModel = json(spec.difficultyModel());
        Level baseLevel = level(spec.baseLevel());
        Optional<TaskTemplate> existing = templates.findByCode(spec.code());
        if (existing.isEmpty()) {
            return templates.save(new TaskTemplate(spec.code(), spec.title(), competencies, baseLevel, difficultyModel, now));
        }
        TaskTemplate template = existing.get();
        boolean changed = !template.getTitle().equals(spec.title())
                || !sameJson(template.getCompetencies(), competencies)
                || template.getBaseLevel() != baseLevel
                || !sameJson(template.getDifficultyModel(), difficultyModel);
        if (changed) {
            template.update(spec.title(), competencies, baseLevel, difficultyModel, now);
        }
        return template;
    }

    private static void addFiles(TaskVariant variant, VariantSpec spec, VariantSources sources) {
        Set<String> editable = Set.copyOf(spec.editable());
        sources.starter().forEach((path, content) -> {
            boolean isEditable = editable.contains(path);
            variant.addFile(isEditable ? FileKind.STARTER : FileKind.READONLY, path, content, isEditable);
        });
        sources.solution().forEach((path, content) -> variant.addFile(FileKind.SOLUTION, path, content, false));
        sources.visibleTests().forEach((path, content) -> variant.addFile(FileKind.VISIBLE_TEST, path, content, false));
        sources.hiddenTests().forEach((path, content) -> variant.addFile(FileKind.HIDDEN_TEST, path, content, false));
    }

    private int disableMissing(Set<String> present) {
        Instant now = clock.instant();
        int disabled = 0;
        for (TaskVariant variant : variants.findByStatus(VariantStatus.VALIDATED)) {
            if (!present.contains(variant.getCode())) {
                variant.disable(now);
                disabled++;
                LOG.warn("Вариант {} отсутствует в банке — отключён", variant.getCode());
            }
        }
        return disabled;
    }

    private static Level level(String value) {
        return Level.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static String readReport(Path variantDir) {
        try {
            return Files.readString(variantDir.resolve(VariantSources.VALIDATION_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read validation.json of " + variantDir, e);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }

    /** jsonb normalises whitespace and key order, so stored values are compared as trees. */
    private static boolean sameJson(String stored, String candidate) {
        try {
            return JSON.readTree(stored).equals(JSON.readTree(candidate));
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static void warn(List<String> warnings, String message) {
        warnings.add(message);
        LOG.warn(message);
    }
}
