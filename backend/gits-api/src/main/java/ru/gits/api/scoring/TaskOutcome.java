package ru.gits.api.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.core.task.TaskKind;

/**
 * What the last submit of a task is worth (docs/indicators.md, «Предварительный балл»). A task counts the hidden
 * tests the starter fails — the ones the solution has to fix. The hidden tests the starter already passes
 * (validator: {@code runs.starter_passing_hidden}) check that nothing got broken: they are not counted, but breaking
 * one makes the task 0. A submit whose code is the starter is 0 too. The warm-up counts the tests of its part 2
 * (it has no hidden tests).
 *
 * @param counted       tests the share is computed from
 * @param countedPassed of them, passed
 * @param guards        hidden tests that check nothing got broken; null when unknown (a task validated before
 *                      validator version 2, or a result stored without test keys): then every hidden test counts
 * @param guardsBroken  of them, not passed
 * @param unchanged     the submitted code is the starter
 * @param share         0..1, four decimals
 */
public record TaskOutcome(int counted, int countedPassed, Integer guards, int guardsBroken, boolean unchanged,
                          BigDecimal share) {

    /**
     * @param testCases  run_result.test_cases of the last submit; null when there is no result
     * @param guardKeys  keys of the hidden tests the starter passes; null when the variant does not say
     */
    public static TaskOutcome of(TaskKind kind, JsonNode testCases, Set<String> guardKeys, boolean unchanged) {
        int counted = 0;
        int countedPassed = 0;
        int guards = 0;
        int guardsBroken = 0;
        boolean calibration = kind == TaskKind.CALIBRATION;
        boolean keysKnown = !calibration && guardKeys != null && testCases != null
                && allHiddenHaveKeys(testCases);
        if (testCases != null) {
            for (JsonNode testCase : testCases) {
                boolean hidden = testCase.path("hidden").asBoolean(false);
                if (hidden == calibration) {
                    // a task is scored on its hidden tests, the warm-up on its visible ones
                    continue;
                }
                boolean passed = "PASSED".equals(testCase.path("status").asText());
                if (keysKnown && guardKeys.contains(testCase.path("key").asText())) {
                    guards++;
                    guardsBroken += passed ? 0 : 1;
                } else {
                    counted++;
                    countedPassed += passed ? 1 : 0;
                }
            }
        }
        BigDecimal share = unchanged || guardsBroken > 0 || counted == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(countedPassed).divide(BigDecimal.valueOf(counted), 4, RoundingMode.HALF_UP);
        return new TaskOutcome(counted, countedPassed, keysKnown ? guards : null, guardsBroken, unchanged, share);
    }

    /** The guard keys of a variant's validation report, or null when the report predates them. */
    public static Set<String> guardKeys(JsonNode validationReport) {
        JsonNode keys = validationReport == null ? null : validationReport.path("runs").get("starter_passing_hidden");
        if (keys == null || !keys.isArray()) {
            return null;
        }
        Set<String> result = new HashSet<>();
        keys.forEach(key -> result.add(key.asText()));
        return result;
    }

    /** The submitted editable files are the starter files, line ends aside. */
    public static boolean unchanged(Map<String, String> submitted, Map<String, String> starter) {
        if (submitted == null || starter.isEmpty()) {
            return false;
        }
        return starter.entrySet().stream().allMatch(file ->
                normalize(file.getValue()).equals(normalize(submitted.getOrDefault(file.getKey(), file.getValue()))));
    }

    /** Why the task got 0 while tests passed, for the report; null when the share is the tests' share. */
    public String zeroReason() {
        if (unchanged) {
            return "Код не изменён — задача не решалась.";
        }
        if (guardsBroken > 0) {
            return "Сломано проверок «ничего не сломано»: " + guardsBroken + " — задача не засчитана.";
        }
        return null;
    }

    private static boolean allHiddenHaveKeys(JsonNode testCases) {
        for (JsonNode testCase : testCases) {
            if (testCase.path("hidden").asBoolean(false) && !testCase.hasNonNull("key")) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").strip();
    }
}
