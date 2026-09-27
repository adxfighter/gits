package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TaskBankCliTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void noArgumentsPrintsUsageAndFails() {
        assertThat(TaskBankCli.run(new String[0], out)).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(output()).contains("Usage:").contains("validate").contains("verify-hashes");
    }

    @Test
    void helpSucceeds() {
        assertThat(TaskBankCli.run(new String[] {"help"}, out)).isEqualTo(TaskBankCli.EXIT_OK);
    }

    @Test
    void unknownCommandFails() {
        assertThat(TaskBankCli.run(new String[] {"frobnicate", "tasks/java"}, out)).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(output()).contains("Unknown command: frobnicate");
    }

    @Test
    void rejectsBadOptions() {
        assertThat(TaskBankCli.run(new String[] {"validate", "tasks/java", "--runs", "0"}, out))
                .isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(TaskBankCli.run(new String[] {"validate", "tasks/java", "--color", "red"}, out))
                .isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(output()).contains("--runs").contains("--color");
    }
}
