package ru.gits.core.telemetry;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.session.SessionTask;

/** A client-side batch of input telemetry events; unique per (session task, seq). */
@Entity
@Table(name = "telemetry_batch")
public class TelemetryBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_task_id", nullable = false)
    private SessionTask sessionTask;

    @Column(nullable = false)
    private int seq;

    @Column(name = "client_ts_start", nullable = false)
    private double clientTsStart;

    @Column(name = "client_ts_end", nullable = false)
    private double clientTsEnd;

    /** JSON array of events, see docs/telemetry.md. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String events;

    /** JSON object with server-side quality flags (e.g. non-monotonic timestamps). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String flags;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected TelemetryBatch() {
    }

    public TelemetryBatch(SessionTask sessionTask, int seq, double clientTsStart, double clientTsEnd,
                          String events, String flags, Instant receivedAt) {
        this.sessionTask = sessionTask;
        this.seq = seq;
        this.clientTsStart = clientTsStart;
        this.clientTsEnd = clientTsEnd;
        this.events = events;
        this.flags = flags;
        this.receivedAt = receivedAt;
    }

    public SessionTask getSessionTask() {
        return sessionTask;
    }

    public int getSeq() {
        return seq;
    }

    public double getClientTsStart() {
        return clientTsStart;
    }

    public double getClientTsEnd() {
        return clientTsEnd;
    }

    public String getEvents() {
        return events;
    }

    public String getFlags() {
        return flags;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
