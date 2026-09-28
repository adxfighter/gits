package ru.gits.api.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.common.Level;
import ru.gits.core.result.SessionIndicatorsRepository;
import ru.gits.core.result.SessionIndicatorsRepository.TaskTrust;
import ru.gits.core.result.SessionScore;
import ru.gits.core.result.SessionScoreRepository;
import ru.gits.core.result.TrustLevel;
import ru.gits.core.session.AssessmentSession;
import ru.gits.core.session.AssessmentSessionRepository;
import ru.gits.core.session.SessionStatus;
import ru.gits.core.session.SessionTaskRepository;
import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.core.task.TaskFileRepository;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskTemplate;
import ru.gits.core.task.TaskVariant;
import ru.gits.core.task.TaskVariantRepository;
import ru.gits.core.task.VariantStatus;

/**
 * The administrator's views (role ADMIN, see SecurityConfig): the whole task bank with validation results and how
 * often each variant was given, one variant with every file (the solution and the hidden tests too), and the sessions
 * of all companies.
 */
@Service
public class AdminService {

    public record VariantRow(UUID id, String code, TaskKind kind, Level level, VariantStatus status, String domain,
                             boolean validationPassed, List<String> failedChecks, Long referenceMaxMs,
                             long issued) {
    }

    public record TemplateRow(String code, String title, Level baseLevel, List<String> competencies,
                              List<VariantRow> variants) {
    }

    public record VariantFile(String path, FileKind kind, boolean editable, String content) {
    }

    public record VariantDetails(UUID id, String code, String templateCode, String templateTitle, TaskKind kind,
                                 Level level, VariantStatus status, int timeLimitMin, String statementMd,
                                 JsonNode validationReport, long issued, List<VariantFile> files) {
    }

    public record SessionRow(UUID sessionId, String companyName, String candidateLabel, Level targetLevel,
                             SessionStatus status, Instant startedAt, Instant finishedAt,
                             BigDecimal preliminaryScore, Instant scoreComputedAt, TrustLevel trustLevel) {
    }

    /** Files in the order an administrator reads a task: what the candidate gets, then what stays hidden. */
    private static final List<FileKind> FILE_ORDER = List.of(FileKind.STARTER, FileKind.READONLY,
            FileKind.VISIBLE_TEST, FileKind.SOLUTION, FileKind.HIDDEN_TEST);

    private final TaskVariantRepository variants;
    private final TaskFileRepository files;
    private final SessionTaskRepository sessionTasks;
    private final AssessmentSessionRepository sessions;
    private final SessionScoreRepository scores;
    private final SessionIndicatorsRepository indicators;
    private final ObjectMapper json;

    public AdminService(TaskVariantRepository variants, TaskFileRepository files, SessionTaskRepository sessionTasks,
                        AssessmentSessionRepository sessions, SessionScoreRepository scores,
                        SessionIndicatorsRepository indicators, ObjectMapper json) {
        this.variants = variants;
        this.files = files;
        this.sessionTasks = sessionTasks;
        this.sessions = sessions;
        this.scores = scores;
        this.indicators = indicators;
        this.json = json;
    }

    /** Templates by code, each with its variants by code. */
    @Transactional(readOnly = true)
    public List<TemplateRow> tasks() {
        Map<UUID, Long> issued = issuedByVariant();
        Map<TaskTemplate, List<TaskVariant>> byTemplate = variants.findAll().stream()
                .collect(Collectors.groupingBy(TaskVariant::getTemplate));
        return byTemplate.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getCode()))
                .map(entry -> {
                    TaskTemplate template = entry.getKey();
                    List<VariantRow> rows = entry.getValue().stream()
                            .sorted(Comparator.comparing(TaskVariant::getCode))
                            .map(variant -> variantRow(variant, issued.getOrDefault(variant.getId(), 0L)))
                            .toList();
                    return new TemplateRow(template.getCode(), template.getTitle(), template.getBaseLevel(),
                            strings(read(template.getCompetencies())), rows);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public VariantDetails variant(UUID variantId) {
        TaskVariant variant = variants.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант не найден"));
        List<VariantFile> all = files.findByVariantId(variantId).stream()
                .sorted(Comparator.comparing((TaskFile file) -> FILE_ORDER.indexOf(file.getKind()))
                        .thenComparing(TaskFile::getPath))
                .map(file -> new VariantFile(file.getPath(), file.getKind(), file.isEditable(), file.getContent()))
                .toList();
        return new VariantDetails(variant.getId(), variant.getCode(), variant.getTemplate().getCode(),
                variant.getTemplate().getTitle(), variant.getKind(), variant.getLevel(), variant.getStatus(),
                variant.getTimeLimitMin(), variant.getStatementMd(), read(variant.getValidationReport()),
                issuedByVariant().getOrDefault(variantId, 0L), all);
    }

    /** Sessions of every company, newest first, with the score and the trust level (warm-up left out, as in P12). */
    @Transactional(readOnly = true)
    public List<SessionRow> sessions() {
        List<AssessmentSession> all = sessions.findAllByOrderByStartedAtDesc();
        if (all.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = all.stream().map(AssessmentSession::getId).toList();
        Map<UUID, SessionScore> scoreBySession = scores.findBySessionIdIn(ids).stream()
                .collect(Collectors.toMap(score -> score.getSession().getId(), Function.identity()));
        Map<UUID, TrustLevel> trustBySession = indicators.findTrustOfSessions(ids).stream()
                .filter(trust -> trust.kind() != TaskKind.CALIBRATION)
                .collect(Collectors.toMap(TaskTrust::sessionId, TaskTrust::trustLevel,
                        (a, b) -> a.compareTo(b) >= 0 ? a : b));
        return all.stream().map(session -> {
            SessionScore score = scoreBySession.get(session.getId());
            var invite = session.getInvite();
            return new SessionRow(session.getId(), invite.getCompany().getName(), invite.getCandidateLabel(),
                    invite.getTargetLevel(), session.getStatus(), session.getStartedAt(), session.getFinishedAt(),
                    score == null ? null : score.getPreliminaryScore(),
                    score == null ? null : score.getComputedAt(), trustBySession.get(session.getId()));
        }).toList();
    }

    private VariantRow variantRow(TaskVariant variant, long issued) {
        JsonNode report = read(variant.getValidationReport());
        List<String> failed = report == null ? List.of() : StreamSupport
                .stream(report.path("checks").spliterator(), false)
                .filter(check -> !check.path("passed").asBoolean(false))
                .map(check -> check.path("id").asText())
                .toList();
        boolean passed = report != null && report.path("checks").size() > 0 && failed.isEmpty();
        JsonNode maxMs = report == null ? null : report.path("runs").path("reference_max_ms");
        return new VariantRow(variant.getId(), variant.getCode(), variant.getKind(), variant.getLevel(),
                variant.getStatus(), variant.getDomain(), passed, failed,
                maxMs == null || !maxMs.isNumber() ? null : maxMs.asLong(), issued);
    }

    private Map<UUID, Long> issuedByVariant() {
        return sessionTasks.countIssuedByVariant().stream()
                .collect(Collectors.toMap(SessionTaskRepository.VariantIssues::getVariantId,
                        SessionTaskRepository.VariantIssues::getIssued));
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON cannot be read", e);
        }
    }
}
