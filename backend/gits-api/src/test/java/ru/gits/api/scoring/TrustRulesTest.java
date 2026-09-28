package ru.gits.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ru.gits.core.result.TrustLevel;

class TrustRulesTest {

    private final TrustRules rules = new TrustRules(List.of(
            new IndicatorProperties.Rule(TrustLevel.YELLOW, List.of("pasteRatio > 0.25"), "some paste"),
            new IndicatorProperties.Rule(TrustLevel.RED, List.of("pasteRatio > 0.5"), "much paste"),
            new IndicatorProperties.Rule(TrustLevel.YELLOW, List.of("burstRelative >= 2.5"), "fast"),
            new IndicatorProperties.Rule(TrustLevel.YELLOW, List.of("telemetryEvents == 0"), "no data")));

    @Test
    void noRuleMeansGreen() {
        var verdict = rules.evaluate(Map.of("pasteRatio", 0.1, "telemetryEvents", 50.0));
        assertThat(verdict.level()).isEqualTo(TrustLevel.GREEN);
        assertThat(verdict.reasons()).isEmpty();
    }

    @Test
    void theWorstMatchingLevelWinsAndAllReasonsAreKept() {
        var verdict = rules.evaluate(Map.of("pasteRatio", 0.7, "telemetryEvents", 50.0));
        assertThat(verdict.level()).isEqualTo(TrustLevel.RED);
        assertThat(verdict.reasons()).containsExactly("some paste", "much paste");
    }

    @Test
    void anUnknownValueNeverTriggersARule() {
        // no calibration baseline: burstRelative is unknown
        assertThat(rules.evaluate(Map.of("pasteRatio", 0.0, "telemetryEvents", 5.0)).level())
                .isEqualTo(TrustLevel.GREEN);
        assertThat(rules.evaluate(Map.of("burstRelative", 2.5, "telemetryEvents", 5.0)).reasons())
                .containsExactly("fast");
    }

    @Test
    void equalityComparison() {
        assertThat(rules.evaluate(Map.of("telemetryEvents", 0.0)).reasons()).containsExactly("no data");
    }

    @Test
    void aMalformedRuleStopsTheStart() {
        assertThatThrownBy(() -> new TrustRules(List.of(
                new IndicatorProperties.Rule(TrustLevel.RED, List.of("pasteRatio >> 1"), "typo"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pasteRatio >> 1");
        assertThatThrownBy(() -> new TrustRules(List.of(
                new IndicatorProperties.Rule(null, List.of("pasteRatio > 1"), "no level"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
