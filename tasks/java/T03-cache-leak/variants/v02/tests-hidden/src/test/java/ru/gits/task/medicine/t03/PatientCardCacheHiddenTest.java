package ru.gits.task.medicine.t03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PatientCardCacheHiddenTest {

    private final List<String> loads = new ArrayList<>();
    private final CardRegistry registry = id -> {
        loads.add(id);
        return new PatientCard(id, "Пациент " + id, List.of("пенициллин"));
    };

    @Test
    void sizeNeverExceedsCapacity() {
        var cache = new PatientCardCache(registry, 500);

        for (int i = 0; i < 20_000; i++) {
            cache.card("P-" + i);
            assertThat(cache.size()).isLessThanOrEqualTo(500);
        }
        assertThat(cache.size()).isEqualTo(500);
    }

    @Test
    void fillingTheCacheEvictsOnlyOneCard() {
        var cache = new PatientCardCache(registry, 3);
        cache.card("P-1");
        cache.card("P-2");
        cache.card("P-3");

        cache.card("P-4");

        assertThat(cache.size()).isEqualTo(3);
        loads.clear();
        cache.card("P-2");
        cache.card("P-3");
        cache.card("P-4");
        assertThat(loads).isEmpty();
    }

    @Test
    void recentlyOpenedCardIsNotEvictedBeforeAnIdleOne() {
        var cache = new PatientCardCache(registry, 2);
        cache.card("P-1");
        cache.card("P-2");
        cache.card("P-1");
        cache.card("P-3");

        loads.clear();
        cache.card("P-1");
        cache.card("P-3");
        cache.card("P-2");

        assertThat(loads).containsExactly("P-2");
    }

    @Test
    void patientSeenByManyDoctorsStaysCached() {
        var cache = new PatientCardCache(registry, 50);
        cache.card("FREQUENT");

        for (int visit = 0; visit < 2_000; visit++) {
            cache.card("P-" + visit);
            if (visit % 10 == 0) {
                cache.card("FREQUENT");
            }
        }
        loads.clear();
        cache.card("FREQUENT");

        assertThat(loads).isEmpty();
    }

    @Test
    void registryIsNotHammeredByARepeatingDailySchedule() {
        var cache = new PatientCardCache(registry, 100);

        // 80 patients, each seen by 5 specialists in a row during the day
        for (int specialist = 0; specialist < 5; specialist++) {
            for (int patient = 0; patient < 80; patient++) {
                cache.card("P-" + patient);
            }
        }

        assertThat(loads).hasSize(80);
    }

    @Test
    void refreshKeepsTheCacheBounded() {
        var cache = new PatientCardCache(registry, 2);

        cache.refresh("P-1");
        cache.refresh("P-2");
        cache.refresh("P-3");

        assertThat(cache.size()).isEqualTo(2);
        loads.clear();
        cache.card("P-3");
        assertThat(loads).isEmpty();
    }
}
