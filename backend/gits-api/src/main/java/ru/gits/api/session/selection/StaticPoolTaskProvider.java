package ru.gits.api.session.selection;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import ru.gits.core.common.Level;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.task.VariantStatus;

/** The validated task bank loaded from tasks/java (P06). */
@Component
public class StaticPoolTaskProvider implements TaskProvider {

    private final TaskVariantRepository variants;

    public StaticPoolTaskProvider(TaskVariantRepository variants) {
        this.variants = variants;
    }

    @Override
    public List<TaskVariant> tasks(Level level) {
        return variants.findByKindAndLevelAndStatus(TaskKind.TASK, level, VariantStatus.VALIDATED);
    }

    @Override
    public List<TaskVariant> calibrationBlocks() {
        return Stream.of(Level.values())
                .flatMap(level -> variants.findByKindAndLevelAndStatus(TaskKind.CALIBRATION, level,
                        VariantStatus.VALIDATED).stream())
                .toList();
    }
}
