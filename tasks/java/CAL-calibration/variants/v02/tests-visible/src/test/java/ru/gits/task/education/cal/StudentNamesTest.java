package ru.gits.task.education.cal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StudentNamesTest {

    @Test
    void wordsStartWithACapitalLetter() {
        assertThat(StudentNames.format("иВАНОВА анна сергеевна")).isEqualTo("Иванова Анна Сергеевна");
    }

    @Test
    void extraSpacesAreRemoved() {
        assertThat(StudentNames.format("   петров    пётр ")).isEqualTo("Петров Пётр");
    }

    @Test
    void emptyNameIsRejected() {
        assertThatThrownBy(() -> StudentNames.format("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
