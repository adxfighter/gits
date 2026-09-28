package ru.gits.runner;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.run.RunJob;
import ru.gits.core.run.RunJobRepository;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunResult;
import ru.gits.core.run.RunResultRepository;
import ru.gits.core.run.RunStatus;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.sandbox.ExpectedTests;
import ru.gits.sandbox.InvalidSourceException;
import ru.gits.sandbox.ParsedRun;
import ru.gits.sandbox.SandboxExecutor;
import ru.gits.sandbox.SandboxOutputParser;
import ru.gits.sandbox.SandboxRun;
import ru.gits.sandbox.SourceArchive;
import ru.gits.sandbox.SourceFile;
import ru.gits.sandbox.TestCaseResult;

/**
 * Executes one claimed job: assembles the files, runs the sandbox outside any transaction and stores the
 * result. Hidden tests never leak: their names and messages are replaced, SUBMIT console output is not
 * stored, and SUBMIT compile errors only show messages about the candidate's own files.
 */
@Component
class RunJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(RunJobProcessor.class);
    private static final int MAX_CONSOLE = 64 * 1024;

    /** Everything needed to run a job, detached from the persistence context. */
    record RunInput(UUID jobId, RunMode mode, List<SourceFile> files, List<SourceFile> candidateFiles,
                    Set<String> hiddenTestClasses, ExpectedTests expected, Set<String> editablePaths) {
    }

    /**
     * One test case as stored in run_result.test_cases. {@code key} ({@link ru.gits.sandbox.TestKey}) — hidden tests
     * only: scoring matches it against the tests the starter passes; it never reaches the candidate.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record StoredTestCase(String name, String status, String message, boolean hidden, String key) {
    }

    private final RunJobRepository jobs;
    private final RunResultRepository results;
    private final SandboxExecutor sandbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;

    RunJobProcessor(RunJobRepository jobs, RunResultRepository results, SandboxExecutor sandbox,
                    TransactionTemplate tx, ObjectMapper json, Clock clock) {
        this.jobs = jobs;
        this.results = results;
        this.sandbox = sandbox;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    void process(UUID jobId) {
        long started = System.nanoTime();
        RunInput input;
        try {
            input = tx.execute(status -> prepare(jobId));
        } catch (InvalidSourceException e) {
            complete(jobId, RunStatus.ERROR, compileFailure(e.getMessage(), 0));
            return;
        }
        Optional<String> violation = ForbiddenApiScanner.findViolation(input.candidateFiles());
        if (violation.isPresent()) {
            complete(jobId, RunStatus.DONE, compileFailure(violation.get(), 0));
            return;
        }
        try {
            SandboxRun run = sandbox.run(SourceArchive.build(input.files()));
            Outcome outcome = interpret(input, run);
            complete(jobId, outcome.status(), outcome.result());
            log.info("job {} {} {} in {} ms (sandbox {} ms)", jobId, input.mode(), outcome.status(),
                    (System.nanoTime() - started) / 1_000_000, run.durationMs());
        } catch (InvalidSourceException e) {
            complete(jobId, RunStatus.ERROR, compileFailure(e.getMessage(), 0));
        } catch (RuntimeException e) {
            log.error("job {} failed", jobId, e);
            complete(jobId, RunStatus.ERROR, compileFailure("Внутренняя ошибка исполнения, попробуйте ещё раз", 0));
        }
    }

    RunInput prepare(UUID jobId) {
        RunJob job = jobs.findWithTaskFiles(jobId).orElseThrow(() -> new IllegalStateException("No job " + jobId));
        Map<String, String> payload = readPayload(job.getPayload());
        List<TaskFile> taskFiles = job.getSessionTask().getVariant().getFiles();

        Set<String> editable = new HashSet<>();
        taskFiles.stream().filter(f -> f.getKind() == FileKind.STARTER && f.isEditable())
                .forEach(f -> editable.add(f.getPath()));
        for (String path : payload.keySet()) {
            if (!editable.contains(path)) {
                throw new InvalidSourceException("Файл нельзя изменять или он не относится к задаче: " + path);
            }
        }

        Map<String, SourceFile> files = new LinkedHashMap<>();
        List<SourceFile> candidate = new ArrayList<>();
        List<SourceFile> tests = new ArrayList<>();
        Set<String> hiddenClasses = new HashSet<>();
        for (TaskFile file : taskFiles) {
            if (!SourceArchive.isSource(file.getPath())) {
                // text files (the warm-up retyping) are checked by the platform, not compiled
                continue;
            }
            switch (file.getKind()) {
                case STARTER -> {
                    String content = file.isEditable() ? payload.getOrDefault(file.getPath(), file.getContent())
                            : file.getContent();
                    SourceFile source = new SourceFile(file.getPath(), content);
                    files.put(file.getPath(), source);
                    if (file.isEditable()) {
                        candidate.add(source);
                    }
                }
                case READONLY -> files.put(file.getPath(), new SourceFile(file.getPath(), file.getContent()));
                case VISIBLE_TEST -> {
                    SourceFile source = new SourceFile(file.getPath(), file.getContent());
                    files.put(file.getPath(), source);
                    tests.add(source);
                }
                case HIDDEN_TEST -> {
                    if (job.getMode() == RunMode.SUBMIT) {
                        SourceFile source = new SourceFile(file.getPath(), file.getContent());
                        files.put(file.getPath(), source);
                        tests.add(source);
                        hiddenClasses.add(className(file.getPath()));
                    }
                }
                case SOLUTION -> {
                    // never sent to the sandbox
                }
            }
        }
        return new RunInput(jobId, job.getMode(), List.copyOf(files.values()), candidate, hiddenClasses,
                ExpectedTests.of(tests), editable);
    }

    record Outcome(RunStatus status, ResultData result) {
    }

    record ResultData(boolean compiled, String compileOutput, int total, int passed, List<StoredTestCase> cases,
                      String console, long durationMs) {
    }

    Outcome interpret(RunInput input, SandboxRun run) {
        boolean submit = input.mode() == RunMode.SUBMIT;
        if (run.timedOut()) {
            return new Outcome(RunStatus.TIMEOUT, new ResultData(true, null, 0, 0, List.of(),
                    "Превышено время выполнения (" + sandbox.config().timeout().toSeconds() + " с)", run.durationMs()));
        }
        if (run.dockerFailed()) {
            log.error("sandbox docker failure for job {}: {}", input.jobId(), run.output());
            return new Outcome(RunStatus.ERROR, compileFailure("Среда исполнения недоступна, попробуйте позже",
                    run.durationMs()));
        }
        ParsedRun parsed = SandboxOutputParser.parse(run);
        if (!parsed.compiled()) {
            String output = submit ? onlyCandidateErrors(parsed.compileOutput(), input.editablePaths())
                    : parsed.compileOutput().replace("/work/src/", "");
            return new Outcome(RunStatus.DONE, compileFailure(output, run.durationMs()));
        }
        String console = submit ? null : truncate(parsed.consoleOutput());
        if (!parsed.reportPresent()) {
            return new Outcome(RunStatus.DONE, new ResultData(true, null, 0, 0, List.of(),
                    submit ? null : truncate("Тесты не завершились (вывод слишком большой или JVM остановлена)\n\n"
                            + parsed.consoleOutput()), run.durationMs()));
        }
        if (!input.expected().matches(parsed.testCases())) {
            log.warn("job {}: JUnit report does not match the expected tests, treating as failed", input.jobId());
            return new Outcome(RunStatus.DONE, new ResultData(true, null, 0, 0, List.of(),
                    "Отчёт о тестах не прошёл проверку целостности", run.durationMs()));
        }
        List<StoredTestCase> cases = new ArrayList<>();
        int hiddenNo = 0;
        int total = 0;
        int passed = 0;
        for (TestCaseResult testCase : parsed.testCases()) {
            boolean hidden = input.hiddenTestClasses().contains(topLevel(testCase.className()));
            if (hidden) {
                hiddenNo++;
                cases.add(new StoredTestCase("Скрытый тест " + hiddenNo, testCase.status().name(), null, true,
                        ru.gits.sandbox.TestKey.of(testCase)));
            } else {
                cases.add(new StoredTestCase(simpleName(testCase.className()) + " › " + testCase.name(),
                        testCase.status().name(), testCase.message(), false, null));
            }
            // SUBMIT is scored on hidden tests; RUN reports the visible tests the candidate can see
            if (hidden == submit) {
                total++;
                if (testCase.passed()) {
                    passed++;
                }
            }
        }
        return new Outcome(RunStatus.DONE, new ResultData(true, null, total, passed, cases, console, run.durationMs()));
    }

    private void complete(UUID jobId, RunStatus status, ResultData data) {
        tx.executeWithoutResult(s -> {
            RunJob job = jobs.findById(jobId).orElseThrow();
            results.save(new RunResult(job, data.compiled(), data.compileOutput(), data.total(), data.passed(),
                    toJson(data.cases()), data.durationMs(), data.console(), null, clock.instant()));
            job.finish(status, clock.instant());
        });
    }

    private static ResultData compileFailure(String message, long durationMs) {
        return new ResultData(false, message, 0, 0, List.of(), null, durationMs);
    }

    /**
     * Keeps javac diagnostics that point at candidate files; diagnostics inside hidden tests would reveal
     * their code.
     */
    static String onlyCandidateErrors(String javacOutput, Set<String> editablePaths) {
        StringBuilder kept = new StringBuilder();
        boolean keep = false;
        for (String line : javacOutput.split("\n", -1)) {
            String trimmed = line.replace("/work/src/", "");
            if (trimmed.matches("^src/[\\w/$.-]+\\.java:\\d+: .*")) {
                keep = editablePaths.stream().anyMatch(trimmed::startsWith);
            } else if (trimmed.matches("^\\d+ errors?$")) {
                keep = false;
            }
            if (keep) {
                kept.append(trimmed).append('\n');
            }
        }
        return kept.isEmpty()
                ? "Решение не компилируется вместе с тестами проверки: проверьте, что вы не меняли сигнатуры публичных методов"
                : kept.toString().strip();
    }

    private Map<String, String> readPayload(String payload) {
        try {
            return json.readValue(payload, new TypeReference<HashMap<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new InvalidSourceException("Некорректный формат файлов решения");
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_CONSOLE ? text : text.substring(0, MAX_CONSOLE);
    }

    private static String className(String path) {
        return path.replaceFirst("^src/(main|test)/java/", "").replace(".java", "").replace('/', '.');
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
