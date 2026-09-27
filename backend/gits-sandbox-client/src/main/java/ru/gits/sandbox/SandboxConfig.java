package ru.gits.sandbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * How sandbox containers are started. The isolation flags are fixed here and mirror
 * sandbox/java/README.md; only the image, runtime, timeout and docker binary are configurable.
 *
 * @param dockerBinary  docker CLI executable ("docker" resolves via PATH)
 * @param image         sandbox image, e.g. gits-sandbox-java:local
 * @param runtime       runc (default) or runsc (gVisor, Linux hosts)
 * @param timeout       wall-clock limit for one run; the container is killed afterwards
 * @param maxOutputBytes cap on captured container output (the entrypoint caps test output at 64 KB,
 *                       the rest is the XML report)
 */
public record SandboxConfig(String dockerBinary, String image, String runtime, Duration timeout, int maxOutputBytes) {

    public static final String LABEL = "gits.sandbox=true";

    public SandboxConfig {
        if (!runtime.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid runtime: " + runtime);
        }
    }

    public static SandboxConfig defaults(String image, String runtime) {
        return new SandboxConfig("docker", image, runtime, Duration.ofSeconds(30), 1024 * 1024);
    }

    /** Full `docker run` command line for a container with the given name. */
    List<String> runCommand(String containerName) {
        List<String> command = new ArrayList<>(List.of(
                dockerBinary, "run", "--rm", "-i",
                "--name", containerName,
                "--network=none",
                "--hostname", "localhost",
                "--read-only",
                "--tmpfs", "/work:rw,nosuid,nodev,size=64m,mode=1777",
                "--tmpfs", "/tmp:rw,nosuid,nodev,size=16m,mode=1777",
                "--memory=768m", "--memory-swap=768m",
                "--cpus=1",
                "--pids-limit=128",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                "--user", "10001:10001",
                "--label", LABEL,
                "--runtime", runtime));
        command.add(image);
        return command;
    }
}
