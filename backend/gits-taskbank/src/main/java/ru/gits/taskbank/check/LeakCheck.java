package ru.gits.taskbank.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visible tests must not contain the solution: any window of {@value #WINDOW} consecutive meaningful
 * solution lines that includes at least one line absent from the starter must not appear in a visible
 * test (whitespace-insensitive).
 */
public final class LeakCheck {

    static final int WINDOW = 4;

    private LeakCheck() {
    }

    /** The first leaked fragment, if any. */
    public static Optional<String> findLeak(Map<String, String> starter, Map<String, String> solution,
                                            Map<String, String> visibleTests) {
        String tests = String.join("\n", normalisedLines(String.join("\n", visibleTests.values())));
        for (var entry : solution.entrySet()) {
            Set<String> starterLines = new HashSet<>(normalisedLines(starter.getOrDefault(entry.getKey(), "")));
            List<String> lines = normalisedLines(entry.getValue());
            for (int i = 0; i + WINDOW <= lines.size(); i++) {
                List<String> window = lines.subList(i, i + WINDOW);
                boolean containsNewCode = window.stream().anyMatch(line -> !starterLines.contains(line));
                String fragment = String.join("\n", window);
                if (containsNewCode && tests.contains(fragment)) {
                    return Optional.of(entry.getKey() + ": " + fragment.replace('\n', ' '));
                }
            }
        }
        return Optional.empty();
    }

    /** Trimmed, whitespace-collapsed lines, without blank lines and lone braces. */
    static List<String> normalisedLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String normalised = line.strip().replaceAll("\\s+", " ");
            if (!normalised.isEmpty() && !normalised.matches("[{}();]+")) {
                lines.add(normalised);
            }
        }
        return lines;
    }
}
