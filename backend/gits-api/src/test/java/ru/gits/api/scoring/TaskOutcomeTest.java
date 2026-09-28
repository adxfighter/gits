package ru.gits.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.task.TaskKind;

/** The share of a task: the tests it had to fix count, the tests of «nothing broken» guard it. */
class TaskOutcomeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode cases(String json) throws Exception {
        return JSON.readTree(json);
    }

    private static final String SUBMIT = """
            [{"name":"VisibleTest › a","status":"PASSED","hidden":false},
             {"name":"Скрытый тест 1","status":"PASSED","hidden":true,"key":"guard1"},
             {"name":"Скрытый тест 2","status":"PASSED","hidden":true,"key":"guard2"},
             {"name":"Скрытый тест 3","status":"PASSED","hidden":true,"key":"fix1"},
             {"name":"Скрытый тест 4","status":"FAILED","hidden":true,"key":"fix2"},
             {"name":"Скрытый тест 5","status":"FAILED","hidden":true,"key":"fix3"}]
            """;

    @Test
    void onlyTheTestsTheStarterFailsCount() throws Exception {
        TaskOutcome outcome = TaskOutcome.of(TaskKind.TASK, cases(SUBMIT), Set.of("guard1", "guard2"), false);
        assertThat(outcome.counted()).isEqualTo(3);
        assertThat(outcome.countedPassed()).isEqualTo(1);
        assertThat(outcome.guards()).isEqualTo(2);
        assertThat(outcome.share()).isEqualByComparingTo("0.3333");
        assertThat(outcome.zeroReason()).isNull();
    }

    @Test
    void theStarterItselfScoresNothing() throws Exception {
        // what the owner saw: an untouched task passed the «nothing broken» tests and got points
        String starter = SUBMIT.replace("\"status\":\"PASSED\",\"hidden\":true,\"key\":\"fix1\"",
                "\"status\":\"FAILED\",\"hidden\":true,\"key\":\"fix1\"");
        TaskOutcome outcome = TaskOutcome.of(TaskKind.TASK, cases(starter), Set.of("guard1", "guard2"), false);
        assertThat(outcome.share()).isEqualByComparingTo("0");
        // and the code is not even changed: 0 whatever flaky test passed
        TaskOutcome unchanged = TaskOutcome.of(TaskKind.TASK, cases(SUBMIT), Set.of("guard1", "guard2"), true);
        assertThat(unchanged.share()).isEqualByComparingTo("0");
        assertThat(unchanged.zeroReason()).contains("Код не изменён");
    }

    @Test
    void breakingWhatWorkedMakesTheTaskZero() throws Exception {
        String broken = SUBMIT.replace("\"status\":\"PASSED\",\"hidden\":true,\"key\":\"guard2\"",
                "\"status\":\"FAILED\",\"hidden\":true,\"key\":\"guard2\"");
        TaskOutcome outcome = TaskOutcome.of(TaskKind.TASK, cases(broken), Set.of("guard1", "guard2"), false);
        assertThat(outcome.guardsBroken()).isEqualTo(1);
        assertThat(outcome.share()).isEqualByComparingTo("0");
        assertThat(outcome.zeroReason()).contains("ничего не сломано");
    }

    @Test
    void withoutKeysEveryHiddenTestCountsAsBefore() throws Exception {
        String old = SUBMIT.replaceAll(",\"key\":\"\\w+\"", "");
        TaskOutcome outcome = TaskOutcome.of(TaskKind.TASK, cases(old), Set.of("guard1"), false);
        assertThat(outcome.guards()).isNull();
        assertThat(outcome.counted()).isEqualTo(5);
        assertThat(outcome.share()).isEqualByComparingTo("0.6");
        // a variant validated before version 2 does not know its guards either
        assertThat(TaskOutcome.of(TaskKind.TASK, cases(SUBMIT), null, false).counted()).isEqualTo(5);
    }

    @Test
    void theWarmUpCountsTheTestsOfItsPart2() throws Exception {
        TaskOutcome outcome = TaskOutcome.of(TaskKind.CALIBRATION, cases("""
                [{"name":"PhoneNumbersTest › a","status":"PASSED","hidden":false},
                 {"name":"PhoneNumbersTest › b","status":"FAILED","hidden":false}]
                """), null, false);
        assertThat(outcome.counted()).isEqualTo(2);
        assertThat(outcome.share()).isEqualByComparingTo("0.5");
        assertThat(TaskOutcome.of(TaskKind.CALIBRATION, null, null, false).share()).isEqualByComparingTo("0");
    }

    @Test
    void guardKeysComeFromTheValidationReportAndTheStarterIsComparedWithoutLineEnds() throws Exception {
        assertThat(TaskOutcome.guardKeys(JSON.readTree("{\"runs\":{\"starter_passing_hidden\":[\"a\",\"b\"]}}")))
                .containsExactlyInAnyOrder("a", "b");
        assertThat(TaskOutcome.guardKeys(JSON.readTree("{\"runs\":{\"requested\":20}}"))).isNull();
        assertThat(TaskOutcome.unchanged(Map.of("A.java", "class A {}\r\n"), Map.of("A.java", "class A {}\n")))
                .isTrue();
        assertThat(TaskOutcome.unchanged(Map.of("A.java", "class A { int x; }"), Map.of("A.java", "class A {}")))
                .isFalse();
    }
}
