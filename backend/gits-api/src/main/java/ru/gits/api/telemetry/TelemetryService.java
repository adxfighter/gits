package ru.gits.api.telemetry;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;

import ru.gits.core.invite.InviteRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTask;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.session.SessionTaskStatus;
import ru.gits.core.telemetry.TelemetryBatch;
import ru.gits.core.telemetry.TelemetryBatchRepository;

/**
 * Intake of telemetry batches (docs/telemetry.md): limits, event validation, idempotency by (task, seq) and
 * quality flags for non-monotonic timestamps. Flagged batches are stored, never rejected.
 */
@Service
public class TelemetryService {

    /**
     * Answer to a batch. {@code beaconToken} is a new one-time token when the previous one was used (JSON requests
     * only); otherwise null.
     */
    public record Accepted(int seq, boolean duplicate, Map<String, Boolean> flags, String beaconToken) {
    }

    private final InviteRepository invites;
    private final SessionTaskRepository sessionTasks;
    private final TelemetryBatchRepository batches;
    private final TelemetryProperties properties;
    /** Strict copy of the application mapper: "seq": 1.9 or "t": "12" is an error, not a silent coercion. */
    private final ObjectMapper json;
    private final Clock clock;

    public TelemetryService(InviteRepository invites, SessionTaskRepository sessionTasks,
                            TelemetryBatchRepository batches, TelemetryProperties properties, ObjectMapper json,
                            Clock clock) {
        this.invites = invites;
        this.sessionTasks = sessionTasks;
        this.batches = batches;
        this.properties = properties;
        this.json = json.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.json.coercionConfigDefaults()
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        this.clock = clock;
    }

    /**
     * Accepts one batch of the candidate's task. {@code beacon} marks a sendBeacon request, authorized by the
     * one-time token in the body instead of the CSRF header.
     */
    @Transactional
    public Accepted accept(UUID inviteId, UUID sessionTaskId, byte[] body, boolean beacon) {
        if (body.length > properties.maxBatchBytes()) {
            throw tooLarge();
        }
        TelemetryBatchRequest request = parse(body);
        // serializes batches of one candidate with each other and with finish/expiry of the session
        invites.lockById(inviteId).orElseThrow(TelemetryService::foreign);
        SessionTask task = sessionTasks.findById(sessionTaskId)
                .filter(found -> found.getSession().getInvite().getId().equals(inviteId))
                .orElseThrow(TelemetryService::foreign);
        if (beacon && !BeaconTokens.matches(task, request.beaconToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недействительный токен отправки");
        }
        if (request.seq() == null || request.seq() < 0 || request.clientTsStart() == null
                || request.clientTsEnd() == null || request.events() == null) {
            throw badRequest("Обязательны seq (не меньше 0), clientTsStart, clientTsEnd и events");
        }
        int seq = request.seq();
        if (batches.existsBySessionTaskIdAndSeq(task.getId(), seq)) {
            return new Accepted(seq, true, Map.of(), beacon ? null : reissue(task));
        }
        if (!isOpen(task)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Задание не выполняется, телеметрия не принимается");
        }
        if (request.events().size() > properties.maxEvents()) {
            throw tooLarge();
        }
        List<TelemetryEvent> events = new ArrayList<>(request.events().size());
        for (int i = 0; i < request.events().size(); i++) {
            TelemetryEvent event = request.events().get(i);
            String error = event == null ? "пустое событие" : event.validate();
            if (error != null) {
                throw badRequest("Событие " + i + ": " + error);
            }
            events.add(event.normalized());
        }
        Map<String, Boolean> flags = flags(task, seq, request, events);
        batches.save(new TelemetryBatch(task, seq, request.clientTsStart(), request.clientTsEnd(), write(events),
                write(flags), clock.instant()));
        if (beacon) {
            // only a stored new batch spends the token; the next JSON batch hands out a new one
            task.consumeBeaconToken();
            return new Accepted(seq, false, flags, null);
        }
        return new Accepted(seq, false, flags, reissue(task));
    }

    /** Timestamp checks inside the batch and against its neighbours by seq. Only raised flags are listed. */
    private Map<String, Boolean> flags(SessionTask task, int seq, TelemetryBatchRequest request,
                                       List<TelemetryEvent> events) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        double start = request.clientTsStart();
        double end = request.clientTsEnd();
        boolean monotonic = true;
        boolean inRange = end >= start;
        for (int i = 0; i < events.size(); i++) {
            double t = events.get(i).t();
            if (i > 0 && t < events.get(i - 1).t()) {
                monotonic = false;
            }
            if (t < start || t > end) {
                inRange = false;
            }
        }
        if (!monotonic) {
            flags.put("tNotMonotonic", true);
        }
        if (!inRange) {
            flags.put("outsideBatchRange", true);
        }
        batches.findFirstBySessionTaskIdAndSeqLessThanOrderBySeqDesc(task.getId(), seq)
                .filter(previous -> start < previous.getClientTsEnd())
                .ifPresent(previous -> flags.put("overlapsPreviousBatch", true));
        batches.findFirstBySessionTaskIdAndSeqGreaterThanOrderBySeqAsc(task.getId(), seq)
                .filter(next -> end > next.getClientTsStart())
                .ifPresent(next -> flags.put("overlapsNextBatch", true));
        return flags;
    }

    /** A new beacon token when the page has used its previous one. */
    private static String reissue(SessionTask task) {
        return task.getBeaconTokenHash() == null ? BeaconTokens.issue(task) : null;
    }

    private boolean isOpen(SessionTask task) {
        var session = task.getSession();
        return task.getStatus() == SessionTaskStatus.IN_PROGRESS && session.getStatus() == SessionStatus.IN_PROGRESS
                && clock.instant().isBefore(session.deadline());
    }

    private TelemetryBatchRequest parse(byte[] body) {
        TelemetryBatchRequest request;
        try {
            request = json.readValue(body, TelemetryBatchRequest.class);
        } catch (IOException e) {
            throw badRequest("Пакет телеметрии не является корректным JSON");
        }
        if (request == null) {
            throw badRequest("Пустой пакет телеметрии");
        }
        return request;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Telemetry is always serializable", e);
        }
    }

    private static ResponseStatusException foreign() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Задание не принадлежит кандидату");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Пакет больше " + properties.maxEvents()
                + " событий или " + properties.maxBatchBytes() / 1024 + " КБ");
    }
}
