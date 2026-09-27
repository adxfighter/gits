package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void repositoryTemplatesAndVariantsMatchTheSchema() throws Exception {
        Path bank = Path.of("../../tasks/java");
        long expectedFiles;
        try (Stream<Path> files = Files.walk(bank)) {
            expectedFiles = files.filter(p -> p.getFileName().toString().matches("template\\.yaml|task\\.yaml")).count();
        }

        int code = TaskBankCli.run(new String[] {"validate", bank.toString(), "--schema-only"}, out);

        assertThat(expectedFiles).as("the repository bank has templates").isGreaterThanOrEqualTo(11);
        assertThat(code).as(output()).isEqualTo(TaskBankCli.EXIT_OK);
        assertThat(output()).contains("проверено файлов " + expectedFiles + ", с ошибками 0");
    }

    @Test
    void schemaOnlyFailsWhenTheSelectionIsEmpty() {
        int code = TaskBankCli.run(new String[] {"validate", "../../tasks/java", "--schema-only", "--variant", "T99"}, out);

        assertThat(code).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(output()).contains("Ничего не найдено для T99");
    }

    @Test
    void schemaOnlyReportsBrokenYamlInsteadOfCrashing(@TempDir Path temp) throws Exception {
        Path template = Files.createDirectories(temp.resolve("tasks/java/T42-broken"));
        Files.writeString(template.resolve("template.yaml"), "code: T42\ntitle: [unclosed\n");
        Path schema = Files.createDirectories(temp.resolve("tasks/schema"));
        for (String name : new String[] {"template.schema.json", "task.schema.json"}) {
            Files.copy(Path.of("../../tasks/schema").resolve(name), schema.resolve(name));
        }

        int code = TaskBankCli.run(new String[] {"validate", temp.resolve("tasks/java").toString(), "--schema-only"}, out);

        assertThat(code).isEqualTo(TaskBankCli.EXIT_FAILED);
        assertThat(output()).contains("T42-broken/template.yaml: не читается как YAML");
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
