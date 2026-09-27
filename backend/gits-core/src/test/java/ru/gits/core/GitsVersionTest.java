package ru.gits.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitsVersionTest {

    @Test
    void displayContainsProductAndVersion() {
        assertThat(GitsVersion.display()).isEqualTo("GITS 1.0.0-SNAPSHOT");
    }
}
