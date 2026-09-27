package ru.gits.core.result;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.session.AssessmentSession;

/** Preliminary (not yet IRT-calibrated) session score on a 0-100 scale. */
@Entity
@Table(name = "session_score")
public class SessionScore extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private AssessmentSession session;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "per_task", nullable = false)
    private String perTask;

    @Column(name = "preliminary_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal preliminaryScore;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected SessionScore() {
    }

    public SessionScore(AssessmentSession session, String perTask, BigDecimal preliminaryScore, Instant computedAt) {
        this.session = session;
        recompute(perTask, preliminaryScore, computedAt);
    }

    public final void recompute(String perTask, BigDecimal preliminaryScore, Instant computedAt) {
        this.perTask = perTask;
        this.preliminaryScore = preliminaryScore;
        this.computedAt = computedAt;
    }

    public AssessmentSession getSession() {
        return session;
    }

    public String getPerTask() {
        return perTask;
    }

    public BigDecimal getPreliminaryScore() {
        return preliminaryScore;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
