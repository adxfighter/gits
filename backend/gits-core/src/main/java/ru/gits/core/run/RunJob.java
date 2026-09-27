package ru.gits.core.run;

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
import ru.gits.core.session.SessionTask;

/** A queued code execution request, processed by gits-runner. */
@Entity
@Table(name = "run_job")
public class RunJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_task_id", nullable = false)
    private SessionTask sessionTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    /** JSON object {path: content} with the candidate's editable files at submission time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected RunJob() {
    }

    public RunJob(SessionTask sessionTask, RunMode mode, String payload, Instant createdAt) {
        this.sessionTask = sessionTask;
        this.mode = mode;
        this.payload = payload;
        this.createdAt = createdAt;
        this.status = RunStatus.QUEUED;
    }

    public void markRunning(String workerId, Instant now) {
        this.status = RunStatus.RUNNING;
        this.workerId = workerId;
        this.startedAt = now;
    }

    public void finish(RunStatus finalStatus, Instant now) {
        if (!finalStatus.isFinal()) {
            throw new IllegalArgumentException("Not a final status: " + finalStatus);
        }
        this.status = finalStatus;
        this.finishedAt = now;
    }

    public SessionTask getSessionTask() {
        return sessionTask;
    }

    public RunMode getMode() {
        return mode;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
