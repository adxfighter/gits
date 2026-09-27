package ru.gits.task.telecom.cal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void internationalFormatWithSeparators() {
        assertThat(PhoneNumbers.normalize("+7 (912) 345-67-89")).isEqualTo("79123456789");
    }

    @Test
    void domesticFormatStartingWithEight() {
        assertThat(PhoneNumbers.normalize("8 912 345 67 89")).isEqualTo("79123456789");
    }

    @Test
    void textThatIsNotAMobileNumberIsRejected() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("12-34")).isInstanceOf(IllegalArgumentException.class);
    }
}
