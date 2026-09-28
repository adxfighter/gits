package ru.gits.api.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.api.support.CandidateSessionTest;

/** Warm-up part 1: the retyping is checked by the platform against Sample.txt, nothing is compiled or run. */
class RetypingApiTest extends CandidateSessionTest {

    private record Warmup(Candidate candidate, UUID taskId, String sample, String typingPath, UUID otherTask) {
    }

    private Warmup openWarmup() throws Exception {
        Candidate candidate = newCandidate();
        List<JsonNode> tasks = list(read(candidate.post("/candidate/session/start").andReturn().getResponse())
                .get("tasks"));
        UUID taskId = id(tasks.get(0));
        JsonNode task = read(candidate.get("/candidate/tasks/" + taskId).andExpect(status().isOk()).andReturn()
                .getResponse());
        assertThat(task.get("kind").asText()).isEqualTo("CALIBRATION");
        String sample = null;
        String typingPath = null;
        for (JsonNode file : task.get("files")) {
            String path = file.get("path").asText();
            if (path.endsWith("/Sample.txt")) {
                assertThat(file.get("editable").asBoolean()).isFalse();
                sample = file.get("content").asText();
            } else if (path.endsWith("/Typing.txt")) {
                assertThat(file.get("editable").asBoolean()).isTrue();
                typingPath = path;
            }
        }
        assertThat(sample).isNotBlank();
        assertThat(typingPath).isNotNull();
        return new Warmup(candidate, taskId, sample, typingPath, id(tasks.get(1)));
    }

    private ResultActions check(Warmup warmup, UUID taskId, Map<String, String> files) throws Exception {
        return mvc.perform(post("/candidate/tasks/" + taskId + "/retyping").cookie(warmup.candidate().cookie())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("files", files))));
    }

    @Test
    void theRetypingIsComparedWithTheSampleIgnoringWhitespace() throws Exception {
        Warmup w = openWarmup();

        JsonNode exact = read(check(w, w.taskId(), Map.of(w.typingPath(), w.sample().replaceAll("\\s+", " ")))
                .andExpect(status().isOk()).andReturn().getResponse());
        assertThat(exact.get("similarityPercent").asDouble()).isEqualTo(100.0);
        assertThat(exact.get("passed").asBoolean()).isTrue();
        assertThat(exact.get("message").asText()).startsWith("Перепечатка засчитана");

        String typos = w.sample().replaceFirst("return", "retrun").replaceFirst("new ", "nwe ");
        JsonNode withTypos = read(check(w, w.taskId(), Map.of(w.typingPath(), typos)).andReturn().getResponse());
        assertThat(withTypos.get("passed").asBoolean()).isTrue();
        assertThat(withTypos.get("similarityPercent").asDouble()).isBetween(95.0, 99.9);

        String half = w.sample().substring(0, w.sample().length() / 2);
        JsonNode incomplete = read(check(w, w.taskId(), Map.of(w.typingPath(), half)).andReturn().getResponse());
        assertThat(incomplete.get("passed").asBoolean()).isFalse();
        assertThat(incomplete.get("message").asText())
                .startsWith("Напечатанный текст отличается от первоначального более 5 %");

        // the files sent with the check are saved, like a run
        JsonNode task = read(w.candidate().get("/candidate/tasks/" + w.taskId()).andReturn().getResponse());
        assertThat(task.get("code").get(w.typingPath()).asText()).isEqualTo(half);
    }

    @Test
    void aPasteIntoTheTypingFileIsASuspicionOfCopying() throws Exception {
        Warmup w = openWarmup();
        String piece = w.sample().substring(0, 60);
        mvc.perform(post("/candidate/tasks/" + w.taskId() + "/telemetry").cookie(w.candidate().cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seq", 0, "clientTsStart", 1, "clientTsEnd", 2,
                                "events", List.of(
                                        Map.of("t", 1, "type", "paste", "file", w.typingPath(), "length", piece.length()),
                                        Map.of("t", 1.5, "type", "edit", "file", w.typingPath(), "rangeOffset", 0,
                                                "rangeLength", 0, "textLength", piece.length(), "text", piece,
                                                "source", "paste"))))))
                .andExpect(status().isOk());

        JsonNode result = read(check(w, w.taskId(), Map.of(w.typingPath(), w.sample())).andReturn().getResponse());

        assertThat(result.get("pasteSuspected").asBoolean()).isTrue();
        assertThat(result.get("passed").asBoolean()).isFalse();
        assertThat(result.get("message").asText()).startsWith("Подозрение на копирование");
    }

    @Test
    void onlyAnOpenWarmupHasARetypingCheck() throws Exception {
        Warmup w = openWarmup();
        w.candidate().get("/candidate/tasks/" + w.otherTask()).andExpect(status().isOk());
        check(w, w.otherTask(), Map.of()).andExpect(status().isBadRequest());

        w.candidate().post("/candidate/tasks/" + w.taskId() + "/submit").andExpect(status().isAccepted());
        check(w, w.taskId(), Map.of()).andExpect(status().isConflict());
    }
}
