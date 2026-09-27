package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TaskBankCliTest {

    @Test
    void noArgumentsPrintsUsageAndFails() {
        var buffer = new ByteArrayOutputStream();

        int code = TaskBankCli.run(new String[0], new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("Usage: gits-taskbank");
    }

    @Test
    void helpSucceeds() {
        var buffer = new ByteArrayOutputStream();

        int code = TaskBankCli.run(new String[] {"help"}, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(TaskBankCli.EXIT_OK);
    }

    @Test
    void unknownCommandFails() {
        var buffer = new ByteArrayOutputStream();

        int code = TaskBankCli.run(new String[] {"frobnicate"}, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(TaskBankCli.EXIT_USAGE);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("Unknown command: frobnicate");
    }
}
