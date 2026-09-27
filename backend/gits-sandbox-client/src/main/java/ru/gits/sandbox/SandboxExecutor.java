package ru.gits.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a source archive in a fresh sandbox container through the docker CLI. The CLI is used instead of
 * a Docker API client because streaming stdin into an attached container and killing it on timeout is
 * reliable with it on both Docker Desktop (Windows) and Linux; see docs/adr/0004-runner.md.
 */
public class SandboxExecutor {

    public static final String CONTAINER_PREFIX = "gits-run-";
    private static final Logger log = LoggerFactory.getLogger(SandboxExecutor.class);

    /** Blocking stdin/stdout pumps; kept off the common ForkJoinPool. */
    private static final ExecutorService IO = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "sandbox-io");
        thread.setDaemon(true);
        return thread;
    });

    private final SandboxConfig config;

    public SandboxExecutor(SandboxConfig config) {
        this.config = config;
    }

    public SandboxConfig config() {
        return config;
    }

    public SandboxRun run(byte[] sourceArchive) {
        String name = CONTAINER_PREFIX + UUID.randomUUID();
        long started = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(config.runCommand(name)).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start docker CLI '" + config.dockerBinary() + "'", e);
        }
        CompletableFuture<Void> stdin =
                CompletableFuture.runAsync(() -> writeAndClose(process.getOutputStream(), sourceArchive), IO);
        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> readBounded(process.getInputStream()), IO);
        boolean clean = false;
        try {
            boolean finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                kill(name);
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
            stdin.exceptionally(e -> null).join();
            String output = new String(stdout.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            clean = finished;
            return new SandboxRun(finished ? process.exitValue() : -1, output, !finished, durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(name);
            throw new IllegalStateException("Interrupted while running sandbox", e);
        } catch (Exception e) {
            kill(name);
            throw new IllegalStateException("Sandbox run failed", e);
        } finally {
            if (!clean) {
                remove(name);  // --rm handles normal exits; make sure killed or failed runs leave nothing
            }
        }
    }

    /** Removes sandbox containers left behind by a crashed runner (name prefix gits-run-). */
    public int removeOrphans() {
        String ids = runCli(List.of(config.dockerBinary(), "ps", "-aq",
                "--filter", "label=" + SandboxConfig.LABEL, "--filter", "name=" + CONTAINER_PREFIX)).strip();
        if (ids.isEmpty()) {
            return 0;
        }
        List<String> idList = ids.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        var command = new java.util.ArrayList<>(List.of(config.dockerBinary(), "rm", "-f"));
        command.addAll(idList);
        runCli(command);
        return idList.size();
    }

    private void kill(String name) {
        runCli(List.of(config.dockerBinary(), "kill", name));
    }

    private void remove(String name) {
        runCli(List.of(config.dockerBinary(), "rm", "-f", name));
    }

    private static String runCli(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("docker command failed: {}", String.join(" ", command), e);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void writeAndClose(OutputStream stdin, byte[] data) {
        try (stdin) {
            stdin.write(data);
        } catch (IOException e) {
            // The container may exit before reading everything (e.g. killed); that is reported via the exit code
            log.debug("Sandbox stdin closed early", e);
        }
    }

    private byte[] readBounded(InputStream in) {
        var buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try (in) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                int room = config.maxOutputBytes() - buffer.size();
                if (room > 0) {
                    buffer.write(chunk, 0, Math.min(room, read));
                }
                // keep draining so the process never blocks on a full pipe
            }
        } catch (IOException e) {
            log.debug("Sandbox output stream closed", e);
        }
        return buffer.toByteArray();
    }
}
