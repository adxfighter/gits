package ru.gits.sandbox;

/**
 * Raw result of one container run.
 *
 * @param exitCode  container exit code (2 = compile error, 125-127 = docker failure), -1 when killed
 * @param output    combined stdout/stderr, truncated to {@link SandboxConfig#maxOutputBytes()}
 * @param timedOut  the run exceeded the timeout and the container was killed
 * @param durationMs wall-clock duration including container start
 */
public record SandboxRun(int exitCode, String output, boolean timedOut, long durationMs) {

    public boolean dockerFailed() {
        return !timedOut && exitCode >= 125 && exitCode <= 127;
    }
}
