package ru.gits.api.session.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ru.gits.core.common.Level;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplate;
import ru.gits.core.task.TaskVariant;

class TaskSelectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private final TaskSelectionService service = new TaskSelectionService();

    /** A bank of {@code templates} templates, each with one variant of every level, plus two calibration blocks. */
    private static FakeProvider bank(int templates) {
        var provider = new FakeProvider();
        for (int t = 1; t <= templates; t++) {
            TaskTemplate template = template(String.format("T%02d", t));
            int v = 1;
            for (Level level : Level.values()) {
                provider.add(variant(template, String.format("T%02d-v%02d", t, v++), TaskKind.TASK, level));
            }
        }
        TaskTemplate calibration = template("CAL");
        provider.add(variant(calibration, "CAL-v01", TaskKind.CALIBRATION, Level.JUNIOR));
        provider.add(variant(calibration, "CAL-v02", TaskKind.CALIBRATION, Level.JUNIOR));
        return provider;
    }

    @Test
    void juniorGetsTwoJuniorTasksAndOneMiddleFromDifferentTemplates() {
        var selection = service.select(Level.JUNIOR, 42, bank(10), Set.of());

        assertThat(levels(selection)).containsExactly(Level.JUNIOR, Level.JUNIOR, Level.MIDDLE);
        assertThat(templates(selection)).doesNotHaveDuplicates();
        assertThat(selection.calibration().getKind()).isEqualTo(TaskKind.CALIBRATION);
    }

    @Test
    void seniorGetsOneMiddleTaskAndTwoSenior() {
        var selection = service.select(Level.SENIOR, 7, bank(10), Set.of());

        assertThat(levels(selection)).containsExactly(Level.MIDDLE, Level.SENIOR, Level.SENIOR);
        assertThat(templates(selection)).doesNotHaveDuplicates();
    }

    @Test
    void middleUsesBothCompositionsDependingOnTheSeed() {
        var bank = bank(10);
        Set<List<Level>> seen = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            seen.add(levels(service.select(Level.MIDDLE, seed, bank, Set.of())));
        }
        assertThat(seen).containsExactlyInAnyOrder(
                List.of(Level.JUNIOR, Level.MIDDLE, Level.SENIOR),
                List.of(Level.MIDDLE, Level.MIDDLE, Level.MIDDLE));
    }

    @Test
    void templatesAlwaysDiffer() {
        var bank = bank(4);
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 100; seed++) {
                assertThat(templates(service.select(level, seed, bank, Set.of())))
                        .as("%s, seed %d", level, seed).doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void sameSeedGivesTheSameChoiceAndSeedsVary() {
        var bank = bank(10);
        var first = service.select(Level.JUNIOR, 1234, bank, Set.of());
        var again = service.select(Level.JUNIOR, 1234, bank, Set.of());

        assertThat(codes(again)).isEqualTo(codes(first));
        Set<List<String>> choices = IntStream.range(0, 30)
                .mapToObj(seed -> codes(service.select(Level.JUNIOR, seed, bank, Set.of())))
                .collect(Collectors.toSet());
        assertThat(choices).hasSizeGreaterThan(5);
    }

    @Test
    void choiceDoesNotDependOnTheOrderOfThePool() {
        var ordered = bank(10);
        var reversed = new FakeProvider();
        List<TaskVariant> all = new ArrayList<>(ordered.all);
        Collections.reverse(all);
        all.forEach(reversed::add);

        assertThat(codes(service.select(Level.SENIOR, 99, reversed, Set.of())))
                .isEqualTo(codes(service.select(Level.SENIOR, 99, ordered, Set.of())));
    }

    @Test
    void variantsOfRecentCandidatesAreAvoidedWhilePossible() {
        var bank = bank(10);
        Set<String> recent = new HashSet<>();
        for (int t = 1; t <= 7; t++) {
            recent.add(String.format("T%02d-v01", t));   // the junior variants of seven templates
        }

        for (long seed = 0; seed < 50; seed++) {
            assertThat(codes(service.select(Level.JUNIOR, seed, bank, recent))).doesNotContainAnyElementsOf(recent);
        }
    }

    @Test
    void recentVariantsAreUsedWhenThePoolIsTooSmall() {
        var bank = bank(3);
        Set<String> recent = Set.of("T01-v01", "T02-v01", "T03-v01");   // every junior variant

        var selection = service.select(Level.JUNIOR, 5, bank, recent);

        assertThat(levels(selection)).containsExactly(Level.JUNIOR, Level.JUNIOR, Level.MIDDLE);
    }

    @Test
    void calibrationBlockIsChosenForAnyLevelAndAvoidsRecentOnes() {
        var bank = bank(10);
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 20; seed++) {
                assertThat(service.select(level, seed, bank, Set.of("CAL-v01")).calibration().getCode())
                        .isEqualTo("CAL-v02");
            }
        }
    }

    @Test
    void middleFallsBackToTheOtherCompositionWhenNoSeniorTasksExist() {
        var provider = new FakeProvider();
        for (int t = 1; t <= 5; t++) {
            TaskTemplate template = template(String.format("T%02d", t));
            provider.add(variant(template, String.format("T%02d-v01", t), TaskKind.TASK, Level.JUNIOR));
            provider.add(variant(template, String.format("T%02d-v02", t), TaskKind.TASK, Level.MIDDLE));
        }
        provider.add(variant(template("CAL"), "CAL-v01", TaskKind.CALIBRATION, Level.JUNIOR));

        for (long seed = 0; seed < 20; seed++) {
            assertThat(levels(service.select(Level.MIDDLE, seed, provider, Set.of())))
                    .containsExactly(Level.MIDDLE, Level.MIDDLE, Level.MIDDLE);
        }
    }

    @Test
    void bankWithTooFewTemplatesIsReported() {
        assertThatThrownBy(() -> service.select(Level.JUNIOR, 1, bank(2), Set.of()))
                .isInstanceOf(TaskSelectionService.NotEnoughTasksException.class);
    }

    @Test
    void bankWithoutCalibrationIsReported() {
        var provider = bank(10);
        provider.all.removeIf(v -> v.getKind() == TaskKind.CALIBRATION);

        assertThatThrownBy(() -> service.select(Level.JUNIOR, 1, provider, Set.of()))
                .isInstanceOf(TaskSelectionService.NotEnoughTasksException.class)
                .hasMessageContaining("калибровочного");
    }

    private static List<Level> levels(TaskSelectionService.Selection selection) {
        return selection.tasks().stream().map(TaskVariant::getLevel).toList();
    }

    private static List<String> templates(TaskSelectionService.Selection selection) {
        return selection.tasks().stream().map(v -> v.getTemplate().getCode()).toList();
    }

    private static List<String> codes(TaskSelectionService.Selection selection) {
        List<String> codes = new ArrayList<>();
        codes.add(selection.calibration().getCode());
        selection.tasks().forEach(v -> codes.add(v.getCode()));
        return codes;
    }

    private static TaskTemplate template(String code) {
        return new TaskTemplate(code, "Шаблон " + code, "[]", Level.MIDDLE, "{}", NOW);
    }

    private static TaskVariant variant(TaskTemplate template, String code, TaskKind kind, Level level) {
        return new TaskVariant(template, code, kind, level, "домен", "{}", "# " + code, 20, "hash", "{}", NOW);
    }

    private static final class FakeProvider implements TaskProvider {
        final List<TaskVariant> all = new ArrayList<>();

        void add(TaskVariant variant) {
            all.add(variant);
        }

        @Override
        public List<TaskVariant> tasks(Level level) {
            return all.stream().filter(v -> v.getKind() == TaskKind.TASK && v.getLevel() == level).toList();
        }

        @Override
        public List<TaskVariant> calibrationBlocks() {
            return all.stream().filter(v -> v.getKind() == TaskKind.CALIBRATION).toList();
        }
    }
}
