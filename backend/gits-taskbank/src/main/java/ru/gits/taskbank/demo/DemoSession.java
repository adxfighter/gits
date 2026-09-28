package ru.gits.taskbank.demo;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.core.common.Level;
import ru.gits.core.run.RunMode;
import ru.gits.core.run.RunStatus;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.task.TaskKind;

/**
 * A recorded assessment session as a file (seed/demo-sessions/*.json, format {@link #FORMAT}): written by the admin
 * export of one session, read by {@code gits-taskbank demo-seed}. Tasks refer to the bank by variant code, so the
 * bank must contain those variants. No token, IP or user agent is kept; the candidate label is the demo's title.
 */
public record DemoSession(String format, String candidateLabel, Level targetLevel, SessionStatus status,
                          int timeLimitMin, long randomSeed, Instant startedAt, Instant finishedAt,
                          List<Task> tasks) {

    public static final String FORMAT = "gits-demo-session/1";

    public record Task(int orderNo, TaskKind kind, String variantCode, SessionTaskStatus status, Instant startedAt,
                       Instant submittedAt, JsonNode currentCode, Instant codeSavedAt, List<Run> runs,
                       List<Batch> telemetry) {
    }

    /** A run of the code; {@code payload} is the code that ran, {@code result} is null when there is none. */
    public record Run(RunMode mode, RunStatus status, Instant createdAt, Instant startedAt, Instant finishedAt,
                      JsonNode payload, Result result) {
    }

    public record Result(boolean compiled, String compileOutput, int testsTotal, int testsPassed, JsonNode testCases,
                         Long durationMs, String stdout, String stderr, Instant createdAt) {
    }

    public record Batch(int seq, double clientTsStart, double clientTsEnd, JsonNode events, JsonNode flags,
                        Instant receivedAt) {
    }
}
