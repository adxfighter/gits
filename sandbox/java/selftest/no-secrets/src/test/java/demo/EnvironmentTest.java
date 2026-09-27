package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.sun.security.auth.module.UnixSystem;

/** Passes only when the code runs as the unprivileged sandbox user with no secrets in its environment. */
class EnvironmentTest {

    private static final Pattern SECRET_NAME = Pattern.compile("(?i).*(pass|secret|token|key|credential|datasource).*");

    @Test
    void runsAsUnprivilegedUser() {
        UnixSystem unix = new UnixSystem();
        assertThat(unix.getUid()).isEqualTo(10001L);
        assertThat(unix.getGid()).isNotZero();
    }

    @Test
    void environmentHasNoSecrets() {
        assertThat(System.getenv().keySet()).noneMatch(name -> SECRET_NAME.matcher(name).matches());
    }

    @Test
    void dockerSocketIsNotReachable() {
        assertThat(Files.exists(Path.of("/var/run/docker.sock"))).isFalse();
    }

    @Test
    void hasNoCapabilities() throws Exception {
        String status = Files.readString(Path.of("/proc/self/status"));
        String effective = status.lines().filter(l -> l.startsWith("CapEff:")).findFirst().orElseThrow();
        assertThat(effective.replace("CapEff:", "").strip()).matches("0+");
    }
}
