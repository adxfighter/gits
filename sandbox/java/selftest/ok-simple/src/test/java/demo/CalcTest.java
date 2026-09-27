package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalcTest {

    @Test
    void sumOfEmptyIsZero() {
        assertThat(Calc.sum(new int[0])).isZero();
    }

    @Test
    void sumAddsValues() {
        assertThat(Calc.sum(new int[] {1, 2, 3})).isEqualTo(6);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "5,5"})
    void maxOfSingleValue(int value, int expected) {
        assertThat(Calc.max(new int[] {value})).isEqualTo(expected);
    }

    @Test
    void maxOfEmptyFails() {
        assertThatThrownBy(() -> Calc.max(new int[0])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mockitoWorks() {
        IntSupplier supplier = mock(IntSupplier.class);
        when(supplier.getAsInt()).thenReturn(42);
        assertThat(supplier.getAsInt()).isEqualTo(42);
    }
}
