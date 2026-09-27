package ru.gits.api.session.selection;

import java.util.List;

import ru.gits.core.common.Level;
import ru.gits.core.task.TaskVariant;

/**
 * Source of task variants for a session. v1.0 serves the validated static bank ({@link StaticPoolTaskProvider});
 * a generating provider (LLM) may later add variants built on the fly, see {@link LlmClient}.
 */
public interface TaskProvider {

    /** Validated task variants of the level. */
    List<TaskVariant> tasks(Level level);

    /** Validated calibration blocks; they are chosen regardless of the invite level. */
    List<TaskVariant> calibrationBlocks();
}
