package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompetencyTitlesTest {

    @TempDir
    Path temp;

    @Test
    void readsTitlesOfTheRepositoryMatrix() {
        assertThat(CompetencyTitles.read(Path.of("../../tasks")))
                .containsEntry("java.concurrency.locking", "Блокировки и взаимные блокировки")
                .doesNotContainValue("");
    }

    @Test
    void missingFileGivesNoTitles() {
        assertThat(CompetencyTitles.read(temp)).isEmpty();
    }
}
