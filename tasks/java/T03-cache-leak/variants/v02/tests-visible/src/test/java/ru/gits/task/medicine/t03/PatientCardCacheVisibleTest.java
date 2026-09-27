package ru.gits.task.medicine.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PatientCardCacheVisibleTest {

    private final List<String> loads = new ArrayList<>();
    private final CardRegistry registry = id -> {
        loads.add(id);
        return new PatientCard(id, "Пациент " + id, List.of());
    };

    @Test
    void cardIsLoadedOnceWhileCached() {
        var cache = new PatientCardCache(registry, 10);

        cache.card("P-1");
        cache.card("P-1");

        assertThat(loads).containsExactly("P-1");
    }

    @Test
    void refreshReloadsTheCard() {
        var cache = new PatientCardCache(registry, 10);
        cache.card("P-1");

        cache.refresh("P-1");

        assertThat(loads).containsExactly("P-1", "P-1");
        assertThat(cache.size()).isEqualTo(1);
    }
}
