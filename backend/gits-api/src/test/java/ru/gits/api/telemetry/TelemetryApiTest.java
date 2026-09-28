package ru.gits.api.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.api.support.CandidateSessionTest;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/** P08 acceptance: idempotency, limits, foreign and closed tasks, timestamp flags and sendBeacon. */
class TelemetryApiTest extends CandidateSessionTest {

    @Autowired TelemetryBatchRepository batches;

    @Test
    void batchIsStoredOnceAndOnlyKnownFieldsAreKept() throws Exception {
        OpenTask task = openTask();
        Map<String, Object> keyDown = new LinkedHashMap<>(Map.of("t", 12.5, "type", "kd", "keyClass", "letter",
                "repeat", false));
        keyDown.put("key", "q"); // the key character must never be stored
        String body = batch(0, 10, 20, List.of(keyDown, edit(15.25, "q"), Map.of("t", 18, "type", "focus")));

        send(task, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.seq").value(0))
                .andExpect(jsonPath("$.duplicate").value(false));
        send(task, body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        List<TelemetryBatch> stored = batches.findBySessionTaskIdOrderBySeq(task.id());
        assertThat(stored).hasSize(1);
        JsonNode events = json.readTree(stored.get(0).getEvents());
        assertThat(events).hasSize(3);
        assertThat(events.get(0).has("key")).isFalse();
        assertThat(events.get(0).get("keyClass").asText()).isEqualTo("letter");
        assertThat(events.get(1).get("text").asText()).isEqualTo("q");
        assertThat(events.get(1).get("source").asText()).isEqualTo("typing");
        assertThat(events.get(1).get("isUndo").asBoolean()).isFalse();
        assertThat(events.get(2).size()).isEqualTo(2);
        assertThat(json.readTree(stored.get(0).getFlags()).isEmpty()).isTrue();
    }

    @Test
    void batchLimits() throws Exception {
        OpenTask task = openTask();
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= 2000; i++) {
            tooMany.add(Map.of("t", i, "type", "blur"));
        }
        send(task, batch(0, 0, 2000, tooMany)).andExpect(status().isPayloadTooLarge());
        send(task, batch(1, 0, 10, List.of(edit(1, "x".repeat(270_000))))).andExpect(status().isPayloadTooLarge());

        List<Map<String, Object>> exactlyAtLimit = new ArrayList<>(tooMany.subList(0, 2000));
        send(task, batch(2, 0, 2000, exactlyAtLimit)).andExpect(status().isOk());
        assertThat(batches.findBySessionTaskIdOrderBySeq(task.id())).hasSize(1);
    }

    @Test
    void invalidEventsAreRejected() throws Exception {
        OpenTask task = openTask();
        send(task, batch(0, 0, 10, List.of(Map.of("t", 1, "type", "mouse")))).andExpect(status().isBadRequest());
        send(task, batch(0, 0, 10, List.of(Map.of("t", 1, "type", "kd", "keyClass", "q", "repeat", false))))
                .andExpect(status().isBadRequest());
        send(task, batch(0, 0, 10, List.of(Map.of("t", 1, "type", "edit", "file", "A.java", "rangeOffset", 0,
                "rangeLength", 0, "textLength", 5, "text", "ab", "source", "typing"))))
                .andExpect(status().isBadRequest());
        send(task, batch(0, 0, 10, List.of(Map.of("t", -1, "type", "blur")))).andExpect(status().isBadRequest());
        send(task, "{not json").andExpect(status().isBadRequest());
        // numbers are not coerced: a fractional seq or a string t is an error
        send(task, "{\"seq\":1.9,\"clientTsStart\":0,\"clientTsEnd\":1,\"events\":[]}")
                .andExpect(status().isBadRequest());
        send(task, "{\"seq\":0,\"clientTsStart\":0,\"clientTsEnd\":1,\"events\":[{\"t\":\"12\",\"type\":\"blur\"}]}")
                .andExpect(status().isBadRequest());
        send(task, batch(0, 0, 10, List.of(Map.of("t", 1, "type", "copy", "file", "a".repeat(301), "length", 1))))
                .andExpect(status().isBadRequest());
        send(task, "{\"clientTsStart\":0,\"clientTsEnd\":1,\"events\":[]}").andExpect(status().isBadRequest());
        assertThat(batches.findBySessionTaskIdOrderBySeq(task.id())).isEmpty();
    }

    @Test
    void anotherCandidatesTaskIsForbidden() throws Exception {
        OpenTask owner = openTask();
        Candidate stranger = newCandidate();
        stranger.post("/candidate/session/start").andExpect(status().isOk());
        String body = batch(0, 0, 10, List.of(Map.of("t", 1, "type", "blur")));

        sendAs(stranger, owner.id(), body).andExpect(status().isForbidden());
        sendAs(stranger, UUID.randomUUID(), body).andExpect(status().isForbidden());
        assertThat(batches.findBySessionTaskIdOrderBySeq(owner.id())).isEmpty();
    }

    @Test
    void telemetryIsAcceptedOnlyWhileTheTaskIsInProgress() throws Exception {
        Candidate candidate = newCandidate();
        List<JsonNode> tasks = list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks"));
        String body = batch(0, 0, 10, List.of(Map.of("t", 1, "type", "blur")));

        UUID notOpened = id(tasks.get(2));
        sendAs(candidate, notOpened, body).andExpect(status().isConflict());

        UUID submitted = id(tasks.get(1));
        candidate.get("/candidate/tasks/" + submitted).andExpect(status().isOk());
        sendAs(candidate, submitted, body).andExpect(status().isOk());
        candidate.post("/candidate/tasks/" + submitted + "/submit").andExpect(status().isAccepted());
        sendAs(candidate, submitted, batch(1, 10, 20, List.of(Map.of("t", 11, "type", "blur"))))
                .andExpect(status().isConflict());
        // a retry of a batch accepted before the task was closed is still answered as a duplicate
        sendAs(candidate, submitted, body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        UUID calibration = id(tasks.get(0));
        candidate.get("/candidate/tasks/" + calibration).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(90));
        sendAs(candidate, calibration, body).andExpect(status().isConflict());
    }

    @Test
    void nonMonotonicTimestampsAreFlaggedNotRejected() throws Exception {
        OpenTask task = openTask();
        send(task, batch(0, 100, 200, List.of(Map.of("t", 150, "type", "blur"), Map.of("t", 120, "type", "focus"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.tNotMonotonic").value(true));
        send(task, batch(1, 180, 300, List.of(Map.of("t", 350, "type", "blur"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.overlapsPreviousBatch").value(true))
                .andExpect(jsonPath("$.flags.outsideBatchRange").value(true));
        send(task, batch(2, 300, 400, List.of(Map.of("t", 310, "type", "blur"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags").isEmpty());

        List<TelemetryBatch> stored = batches.findBySessionTaskIdOrderBySeq(task.id());
        assertThat(stored).hasSize(3);
        assertThat(json.readTree(stored.get(0).getFlags()).get("tNotMonotonic").asBoolean()).isTrue();
    }

    @Test
    void sendBeaconUsesTheOneTimeTokenInsteadOfCsrf() throws Exception {
        OpenTask task = openTask();
        assertThat(task.beaconToken()).isNotBlank();
        String events = "\"events\":[{\"t\":1,\"type\":\"visibility\",\"state\":\"hidden\"}]";

        // a text/plain batch without the token is not accepted
        beacon(task, "{\"seq\":0,\"clientTsStart\":0,\"clientTsEnd\":2," + events + "}")
                .andExpect(status().isForbidden());
        beacon(task, beaconBody(0, "wrong-token", events)).andExpect(status().isForbidden());

        beacon(task, beaconBody(0, task.beaconToken(), events)).andExpect(status().isOk());
        // the token works once
        beacon(task, beaconBody(1, task.beaconToken(), events)).andExpect(status().isForbidden());
        assertThat(batches.findBySessionTaskIdOrderBySeq(task.id())).hasSize(1);

        // JSON batches still need the CSRF header
        mvc.perform(post(path(task.id())).cookie(task.candidate().cookie()).contentType(MediaType.APPLICATION_JSON)
                .content(batch(1, 2, 3, List.of()))).andExpect(status().isForbidden());

        // the next JSON batch hands the page a new beacon token
        String next = read(send(task, batch(1, 2, 3, List.of())).andExpect(status().isOk()).andReturn()
                .getResponse()).get("beaconToken").asText();
        assertThat(next).isNotBlank().isNotEqualTo(task.beaconToken());
        beacon(task, beaconBody(2, next, events)).andExpect(status().isOk());
        assertThat(batches.findBySessionTaskIdOrderBySeq(task.id())).hasSize(3);
    }

    @Test
    void duplicateOrRejectedBeaconDoesNotSpendTheToken() throws Exception {
        OpenTask task = openTask();
        String events = "\"events\":[{\"t\":1,\"type\":\"blur\"}]";
        send(task, batch(0, 0, 2, List.of(Map.of("t", 1, "type", "blur")))).andExpect(status().isOk());

        // the JSON batch with seq 0 already landed: the beacon of the same batch is a duplicate
        beacon(task, beaconBody(0, task.beaconToken(), events)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        beacon(task, beaconBody(1, task.beaconToken(), "\"events\":[{\"t\":1,\"type\":\"mouse\"}]"))
                .andExpect(status().isBadRequest());
        beacon(task, beaconBody(1, task.beaconToken(), events)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
        beacon(task, beaconBody(2, task.beaconToken(), events)).andExpect(status().isForbidden());
    }

    @Test
    void beaconToAForeignClosedOrExpiredTask() throws Exception {
        OpenTask owner = openTask();
        OpenTask stranger = openTask();
        String events = "\"events\":[{\"t\":1,\"type\":\"blur\"}]";
        // the stranger's own token does not open someone else's task
        mvc.perform(post(path(owner.id())).cookie(stranger.candidate().cookie()).contentType("text/plain")
                .content(beaconBody(0, stranger.beaconToken(), events))).andExpect(status().isForbidden());

        owner.candidate().post("/candidate/tasks/" + owner.id() + "/submit").andExpect(status().isAccepted());
        // submit withdraws the token
        beacon(owner, beaconBody(0, owner.beaconToken(), events)).andExpect(status().isForbidden());

        clock.advance(Duration.ofMinutes(90));
        beacon(stranger, beaconBody(0, stranger.beaconToken(), events)).andExpect(status().isConflict());
        assertThat(batches.findBySessionTaskIdOrderBySeq(owner.id())).isEmpty();
        assertThat(batches.findBySessionTaskIdOrderBySeq(stranger.id())).isEmpty();
    }

    @Test
    void beaconIsRecognizedBehindTheApiContextPath() throws Exception {
        OpenTask task = openTask();
        mvc.perform(post("/api" + path(task.id())).contextPath("/api").cookie(task.candidate().cookie())
                        .contentType("text/plain;charset=UTF-8")
                        .content(beaconBody(0, task.beaconToken(), "\"events\":[]")))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentBatchesWithTheSameSeqAreStoredOnce() throws Exception {
        OpenTask task = openTask();
        String body = batch(0, 0, 10, List.of(Map.of("t", 1, "type", "blur")));

        var responses = concurrently(() -> send(task, body).andReturn().getResponse());

        List<Boolean> duplicates = new ArrayList<>();
        for (var response : responses) {
            assertThat(response.getStatus()).isEqualTo(200);
            duplicates.add(read(response).get("duplicate").asBoolean());
        }
        assertThat(duplicates).containsExactlyInAnyOrder(true, false);
        assertThat(batches.findBySessionTaskIdOrderBySeq(task.id())).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record OpenTask(Candidate candidate, UUID id, String beaconToken) {
    }

    /** A candidate with a started session and an opened (IN_PROGRESS) task. */
    private OpenTask openTask() throws Exception {
        Candidate candidate = newCandidate();
        UUID taskId = id(list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks")).get(1));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andExpect(status().isOk()).andReturn()
                .getResponse());
        return new OpenTask(candidate, taskId, task.get("beaconToken").asText());
    }

    private ResultActions send(OpenTask task, String body) throws Exception {
        return sendAs(task.candidate(), task.id(), body);
    }

    private ResultActions sendAs(Candidate candidate, UUID taskId, String body) throws Exception {
        return mvc.perform(post(path(taskId)).cookie(candidate.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** What navigator.sendBeacon sends: text/plain, the candidate cookie, no CSRF header. */
    private ResultActions beacon(OpenTask task, String body) throws Exception {
        return mvc.perform(post(path(task.id())).cookie(task.candidate().cookie())
                .contentType("text/plain;charset=UTF-8").content(body));
    }

    private static String path(UUID taskId) {
        return "/candidate/tasks/" + taskId + "/telemetry";
    }

    private static String beaconBody(int seq, String token, String events) {
        return "{\"seq\":" + seq + ",\"clientTsStart\":0,\"clientTsEnd\":2,\"beaconToken\":\"" + token + "\","
                + events + "}";
    }

    private String batch(int seq, double start, double end, List<? extends Map<String, ?>> events)
            throws Exception {
        return json.writeValueAsString(Map.of("seq", seq, "clientTsStart", start, "clientTsEnd", end,
                "events", events));
    }

    private static Map<String, Object> edit(double t, String text) {
        return Map.of("t", t, "type", "edit", "file", "src/main/java/Solution.java", "rangeOffset", 0,
                "rangeLength", 0, "textLength", text.length(), "text", text, "source", "typing");
    }
}
