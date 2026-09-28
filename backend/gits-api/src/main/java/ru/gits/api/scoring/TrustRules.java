package ru.gits.api.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.gits.core.result.TrustLevel;

/**
 * Trust level of a task from its indicators by the configured rules (gits.indicators.rules). The level is the worst
 * one among the rules that hold; GREEN when none does. Experimental rules v1.0, see docs/indicators.md.
 */
final class TrustRules {

    record Verdict(TrustLevel level, List<String> reasons) {
    }

    private record Condition(String name, String op, double value) {

        boolean holds(Map<String, Double> values) {
            Double actual = values.get(name);
            if (actual == null) {
                return false;
            }
            return switch (op) {
                case ">" -> actual > value;
                case ">=" -> actual >= value;
                case "<" -> actual < value;
                case "<=" -> actual <= value;
                default -> actual == value;
            };
        }
    }

    private record CompiledRule(TrustLevel level, List<Condition> conditions, String explanation) {
    }

    private static final Pattern CONDITION = Pattern.compile("^\\s*(\\w+)\\s*(>=|<=|==|>|<)\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*$");

    private final List<CompiledRule> rules = new ArrayList<>();

    /** Fails fast on a malformed rule, so a typo in application.yml stops the start instead of passing silently. */
    TrustRules(List<IndicatorProperties.Rule> configured) {
        for (IndicatorProperties.Rule rule : configured == null ? List.<IndicatorProperties.Rule>of() : configured) {
            if (rule.level() == null || rule.when() == null || rule.when().isEmpty()) {
                throw new IllegalArgumentException("Trust rule needs a level and conditions: " + rule);
            }
            List<Condition> conditions = new ArrayList<>();
            for (String text : rule.when()) {
                Matcher matcher = CONDITION.matcher(text);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Malformed trust rule condition: '" + text + "'");
                }
                if (!IndicatorCalculator.NAMES.contains(matcher.group(1))) {
                    throw new IllegalArgumentException("Unknown indicator in trust rule: '" + text + "'");
                }
                conditions.add(new Condition(matcher.group(1), matcher.group(2), Double.parseDouble(matcher.group(3))));
            }
            rules.add(new CompiledRule(rule.level(), List.copyOf(conditions), rule.explanation()));
        }
    }

    Verdict evaluate(Map<String, Double> values) {
        TrustLevel level = TrustLevel.GREEN;
        List<String> reasons = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (rule.conditions().stream().allMatch(condition -> condition.holds(values))) {
                reasons.add(rule.explanation());
                if (rule.level().ordinal() > level.ordinal()) {
                    level = rule.level();
                }
            }
        }
        return new Verdict(level, List.copyOf(reasons));
    }
}
