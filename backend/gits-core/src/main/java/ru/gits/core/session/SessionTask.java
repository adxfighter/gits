package ru.gits.core.session;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.task.TaskKind;
import ru.gits.core.task.TaskVariant;

/** One task (or the calibration block) inside an assessment session. */
@Entity
@Table(name = "session_task")
public class SessionTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private TaskVariant variant;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionTaskStatus status;

    /** JSON object {path: content} with the candidate's editable files. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_code")
    private String currentCode;

    @Column(name = "code_saved_at")
    private Instant codeSavedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** SHA-256 of the one-time token for a sendBeacon telemetry batch; null when none is issued. */
    @Column(name = "beacon_token_hash", length = 64)
    private String beaconTokenHash;

    protected SessionTask() {
    }

    public SessionTask(AssessmentSession session, TaskVariant variant, int orderNo, TaskKind kind) {
        this.session = session;
        this.variant = variant;
        this.orderNo = orderNo;
        this.kind = kind;
        this.status = SessionTaskStatus.NOT_STARTED;
    }

    public void start(Instant now) {
        if (status == SessionTaskStatus.NOT_STARTED) {
            this.status = SessionTaskStatus.IN_PROGRESS;
            this.startedAt = now;
        }
    }

    public void saveCode(String codeJson, Instant now) {
        this.currentCode = codeJson;
        this.codeSavedAt = now;
    }

    public void submit(Instant now) {
        this.status = SessionTaskStatus.SUBMITTED;
        this.submittedAt = now;
        this.beaconTokenHash = null;
    }

    public void issueBeaconToken(String tokenHash) {
        this.beaconTokenHash = tokenHash;
    }

    public void consumeBeaconToken() {
        this.beaconTokenHash = null;
    }

    public String getBeaconTokenHash() {
        return beaconTokenHash;
    }

    public AssessmentSession getSession() {
        return session;
    }

    public TaskVariant getVariant() {
        return variant;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public TaskKind getKind() {
        return kind;
    }

    public SessionTaskStatus getStatus() {
        return status;
    }

    public String getCurrentCode() {
        return currentCode;
    }

    public Instant getCodeSavedAt() {
        return codeSavedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
