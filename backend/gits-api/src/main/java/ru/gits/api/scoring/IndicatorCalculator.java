package ru.gits.api.scoring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indicators of one task from its telemetry (docs/indicators.md). A pure function of the events, so every indicator
 * is checked on synthetic streams in the tests.
 */
final class IndicatorCalculator {

    /** Everything computed for one task; {@code burstRelative} and {@code timeToFirstRunSeconds} may be unknown. */
    record TaskIndicators(double pasteRatio, int pastedChars, int largestPaste, int externalPastes, int focusLossCount,
                          double focusLossSeconds, double burstMax, Double burstRelative, int idleThenBurst,
                          double linearity, double editRatio, int typedChars, Double timeToFirstRunSeconds,
                          int runsCount, int telemetryEvents) {

        /** Values by indicator name, as the trust rules refer to them; unknown values are left out. */
        Map<String, Double> values() {
            Map<String, Double> values = new HashMap<>();
            values.put("pasteRatio", pasteRatio);
            values.put("pastedChars", (double) pastedChars);
            values.put("largestPaste", (double) largestPaste);
            values.put("externalPastes", (double) externalPastes);
            values.put("focusLossCount", (double) focusLossCount);
            values.put("focusLossSeconds", focusLossSeconds);
            values.put("burstMax", burstMax);
            if (burstRelative != null) {
                values.put("burstRelative", burstRelative);
            }
            values.put("idleThenBurst", (double) idleThenBurst);
            values.put("linearity", linearity);
            values.put("editRatio", editRatio);
            values.put("typedChars", (double) typedChars);
            if (timeToFirstRunSeconds != null) {
                values.put("timeToFirstRun", timeToFirstRunSeconds);
            }
            values.put("runsCount", (double) runsCount);
            values.put("telemetryEvents", (double) telemetryEvents);
            return values;
        }
    }

    private final IndicatorProperties properties;

    IndicatorCalculator(IndicatorProperties properties) {
        this.properties = properties;
    }

    /**
     * @param events         the task's telemetry in any order
     * @param finalCodeChars size of the editable code that was checked (characters)
     * @param baseSpeed      the candidate's typing speed from the calibration block (chars/s), null if unknown
     */
    /** Names the trust rules may refer to. */
    static final Set<String> NAMES = Set.of("pasteRatio", "pastedChars", "largestPaste", "externalPastes", "focusLossCount",
            "focusLossSeconds", "burstMax", "burstRelative", "idleThenBurst", "linearity", "editRatio", "typedChars",
            "timeToFirstRun", "runsCount", "telemetryEvents");

    TaskIndicators compute(List<InputEvent> events, int finalCodeChars, Double baseSpeed,
                           Double timeToFirstRunSeconds, int runsCount) {
        List<InputEvent> ordered = events.stream().sorted(Comparator.comparingDouble(InputEvent::t)).toList();
        int inserted = 0;
        int deleted = 0;
        int typed = 0;
        for (InputEvent event : ordered) {
            if (event.isEdit()) {
                inserted += event.textLength();
                deleted += event.rangeLength();
                if (event.isTyped()) {
                    typed += event.textLength();
                }
            }
        }
        List<Integer> pastes = foreignPastes(ordered);
        int pasted = pastes.stream().mapToInt(Integer::intValue).sum();
        int largestPaste = pastes.stream().mapToInt(Integer::intValue).max().orElse(0);
        int externalPastes = (int) pastes.stream().filter(size -> size >= EXTERNAL_PASTE_MIN).count();
        double pasteRatio = finalCodeChars <= 0 ? 0 : Math.min(1.0, (double) pasted / finalCodeChars);
        double[] focus = focusLoss(ordered);
        double burstMax = burstMax(ordered);
        Double burstRelative = baseSpeed == null || baseSpeed <= 0 ? null : burstMax / baseSpeed;
        return new TaskIndicators(round(pasteRatio), pasted, largestPaste, externalPastes, (int) focus[0],
                round(focus[1]),
                round(burstMax), burstRelative == null ? null : round(burstRelative), idleThenBurst(ordered),
                round(linearity(ordered)), inserted == 0 ? 0 : round((double) deleted / inserted), typed,
                timeToFirstRunSeconds == null ? null : round(timeToFirstRunSeconds), runsCount, events.size());
    }

    /**
     * A paste this long (characters) of text found neither in the task's code nor in its statement is a suspicion
     * of copying.
     */
    static final int EXTERNAL_PASTE_MIN = 10;

    /**
     * Sizes of pastes that brought text from outside: a paste undone right away does not count, nor one of the
     * candidate's own code or the statement — found there at the moment of the paste ({@code ownCode}), or copied
     * or cut in the editor before (moving code around).
     */
    static List<Integer> foreignPastes(List<InputEvent> ordered) {
        List<Integer> pastes = new ArrayList<>();
        Set<Integer> copied = new HashSet<>();
        Integer lastPaste = null;
        for (InputEvent event : ordered) {
            if ("copy".equals(event.type()) && event.length() > 0) {
                copied.add(event.length());
            } else if (event.isPaste()) {
                lastPaste = null;
                if (event.isExternalPaste() && !copied.contains(event.textLength())) {
                    pastes.add(event.textLength());
                    lastPaste = pastes.size() - 1;
                }
            } else if (event.isEdit()) {
                if (lastPaste != null && event.undo() && event.rangeLength() == pastes.get(lastPaste)) {
                    pastes.remove((int) lastPaste);
                }
                lastPaste = null;
            }
        }
        return pastes;
    }

    /**
     * Episodes of leaving the task (window blur or hidden tab) and their total length in seconds. Any activity on
     * the page ends an episode — also after a reload, where the new page may not report focus again.
     */
    static double[] focusLoss(List<InputEvent> ordered) {
        boolean blurred = false;
        boolean hidden = false;
        double awaySince = 0;
        int episodes = 0;
        double seconds = 0;
        for (InputEvent event : ordered) {
            boolean wasAway = blurred || hidden;
            switch (event.type()) {
                case "blur" -> blurred = true;
                case "focus" -> blurred = false;
                case "visibility" -> hidden = "hidden".equals(event.state());
                default -> {
                    if (wasAway && event.isActivity()) {
                        blurred = false;
                        hidden = false;
                    }
                }
            }
            boolean away = blurred || hidden;
            if (!wasAway && away) {
                episodes++;
                awaySince = event.t();
            } else if (wasAway && !away) {
                seconds += (event.t() - awaySince) / 1000;
            }
        }
        if (blurred || hidden) {
            double end = ordered.get(ordered.size() - 1).t();
            seconds += (end - awaySince) / 1000;
        }
        return new double[] {episodes, seconds};
    }

    /**
     * The highest typing speed in characters per second over a sliding window, counted in key presses that type a
     * character: pastes, completions and what the editor inserts by itself (indentation, closing brackets) do not
     * count.
     */
    double burstMax(List<InputEvent> ordered) {
        double window = properties.burstWindow().toMillis();
        Deque<InputEvent> inWindow = new ArrayDeque<>();
        int best = 0;
        for (InputEvent event : ordered) {
            if (!event.isCharacterKey()) {
                continue;
            }
            inWindow.addLast(event);
            while (event.t() - inWindow.peekFirst().t() >= window) {
                inWindow.removeFirst();
            }
            best = Math.max(best, inWindow.size());
        }
        return best / (window / 1000);
    }

    /**
     * Episodes "a pause without input longer than idle-pause, then more than idle-burst-chars typed within
     * idle-burst-window, with no paste in that window". The time before the first input (reading the statement)
     * and the time a page was closed (not on the t scale) are not pauses.
     */
    int idleThenBurst(List<InputEvent> ordered) {
        List<InputEvent> input = ordered.stream().filter(e -> e.isEdit() || "kd".equals(e.type())).toList();
        double pause = properties.idlePause().toMillis();
        double window = properties.idleBurstWindow().toMillis();
        int episodes = 0;
        for (int i = 1; i < input.size(); i++) {
            double start = input.get(i).t();
            if (start - input.get(i - 1).t() <= pause) {
                continue;
            }
            int chars = 0;
            boolean pasted = false;
            for (int j = i; j < input.size() && input.get(j).t() - start <= window; j++) {
                InputEvent event = input.get(j);
                pasted |= event.isPaste();
                if (event.isEdit() && !event.undo()) {
                    chars += event.textLength();
                }
            }
            if (!pasted && chars > properties.idleBurstChars()) {
                episodes++;
            }
        }
        return episodes;
    }

    /**
     * Share of edits made at the typing frontier of their file — continuing where the text ends so far, top to
     * bottom without going back — among all edits. The frontier moves with edits before it.
     */
    double linearity(List<InputEvent> ordered) {
        Map<String, Integer> frontier = new HashMap<>();
        int edits = 0;
        int linear = 0;
        for (InputEvent event : ordered) {
            if (!event.isEdit()) {
                continue;
            }
            edits++;
            String file = event.file() == null ? "" : event.file();
            Integer front = frontier.get(file);
            boolean insertion = event.rangeLength() == 0 && event.textLength() > 0;
            if (insertion && (front == null || event.rangeOffset() >= front - properties.linearTolerance())) {
                linear++;
                frontier.put(file, Math.max(front == null ? 0 : front, event.rangeOffset() + event.textLength()));
            } else if (front != null && event.rangeOffset() < front) {
                frontier.put(file, Math.max(0, front + event.textLength() - event.rangeLength()));
            }
        }
        return edits == 0 ? 0 : (double) linear / edits;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
