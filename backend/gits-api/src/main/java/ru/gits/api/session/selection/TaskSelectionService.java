package ru.gits.api.session.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import ru.gits.core.common.Level;
import ru.gits.core.task.TaskVariant;

/**
 * Chooses the tasks of a session: one calibration block and three tasks of different templates whose levels follow
 * the invite level. Pure logic: the same seed, pool and exclusions give the same choice, so a session can be
 * reproduced from its stored random_seed.
 *
 * <ul>
 *   <li>JUNIOR: junior, junior, middle;</li>
 *   <li>MIDDLE: junior, middle, senior or middle ×3 (chosen by the seed, the other one if the first is impossible);</li>
 *   <li>SENIOR: middle, senior, senior.</li>
 * </ul>
 * Variants given to the company's latest candidates ({@code avoid}) are left out while the pool allows it; if it
 * does not, they are used rather than failing the session.
 */
@Service
public class TaskSelectionService {

    static final int TASKS = 3;

    /** The bank cannot fill a session of this level. */
    public static class NotEnoughTasksException extends RuntimeException {

        NotEnoughTasksException(String message) {
            super(message);
        }
    }

    /** Chosen variants: the calibration block first, then the tasks in the order they are offered. */
    public record Selection(TaskVariant calibration, List<TaskVariant> tasks) {
    }

    public Selection select(Level target, long seed, TaskProvider provider, Set<String> avoidCodes) {
        Random random = new Random(seed);
        TaskVariant calibration = pickCalibration(provider.calibrationBlocks(), avoidCodes, random)
                .orElseThrow(() -> new NotEnoughTasksException("В банке нет калибровочного блока"));
        List<List<Level>> compositions = compositions(target, random);
        Map<Level, List<TaskVariant>> pools = new EnumMap<>(Level.class);
        for (boolean avoidRecent : List.of(true, false)) {
            for (List<Level> composition : compositions) {
                Optional<List<TaskVariant>> tasks = assign(composition, provider, pools,
                        avoidRecent ? avoidCodes : Set.of(), random);
                if (tasks.isPresent()) {
                    return new Selection(calibration, tasks.get());
                }
            }
        }
        throw new NotEnoughTasksException("В банке недостаточно задач разных шаблонов для уровня " + target);
    }

    /** Level sets for the invite level; the first one is preferred. */
    static List<List<Level>> compositions(Level target, Random random) {
        return switch (target) {
            case JUNIOR -> List.of(List.of(Level.JUNIOR, Level.JUNIOR, Level.MIDDLE));
            case MIDDLE -> {
                List<Level> spread = List.of(Level.JUNIOR, Level.MIDDLE, Level.SENIOR);
                List<Level> flat = List.of(Level.MIDDLE, Level.MIDDLE, Level.MIDDLE);
                yield random.nextBoolean() ? List.of(spread, flat) : List.of(flat, spread);
            }
            case SENIOR -> List.of(List.of(Level.MIDDLE, Level.SENIOR, Level.SENIOR));
        };
    }

    private static Optional<TaskVariant> pickCalibration(List<TaskVariant> blocks, Set<String> avoid, Random random) {
        List<TaskVariant> sorted = sortedByCode(blocks);
        List<TaskVariant> fresh = sorted.stream().filter(v -> !avoid.contains(v.getCode())).toList();
        List<TaskVariant> from = fresh.isEmpty() ? sorted : fresh;
        return from.isEmpty() ? Optional.empty() : Optional.of(from.get(random.nextInt(from.size())));
    }

    /**
     * Shuffles each level pool with the seed and backtracks over the slots so that the templates differ; the pools
     * are shuffled once per level, so the result depends only on the seed and the pool content.
     */
    private static Optional<List<TaskVariant>> assign(List<Level> composition, TaskProvider provider,
                                                      Map<Level, List<TaskVariant>> pools, Set<String> avoid,
                                                      Random random) {
        List<List<TaskVariant>> candidates = new ArrayList<>();
        for (Level level : composition) {
            List<TaskVariant> pool = pools.computeIfAbsent(level, l -> {
                List<TaskVariant> shuffled = sortedByCode(provider.tasks(l));
                Collections.shuffle(shuffled, random);
                return shuffled;
            });
            candidates.add(pool.stream().filter(v -> !avoid.contains(v.getCode())).toList());
        }
        List<TaskVariant> chosen = new ArrayList<>();
        return backtrack(candidates, 0, chosen, new HashSet<>()) ? Optional.of(List.copyOf(chosen)) : Optional.empty();
    }

    private static boolean backtrack(List<List<TaskVariant>> candidates, int slot, List<TaskVariant> chosen,
                                     Set<String> usedTemplates) {
        if (slot == candidates.size()) {
            return true;
        }
        for (TaskVariant variant : candidates.get(slot)) {
            String template = variant.getTemplate().getCode();
            if (usedTemplates.add(template)) {
                chosen.add(variant);
                if (backtrack(candidates, slot + 1, chosen, usedTemplates)) {
                    return true;
                }
                chosen.remove(chosen.size() - 1);
                usedTemplates.remove(template);
            }
        }
        return false;
    }

    private static List<TaskVariant> sortedByCode(List<TaskVariant> variants) {
        List<TaskVariant> sorted = new ArrayList<>(variants);
        sorted.sort(Comparator.comparing(TaskVariant::getCode));
        return sorted;
    }
}
