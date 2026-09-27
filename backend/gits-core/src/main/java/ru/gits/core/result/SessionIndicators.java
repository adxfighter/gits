package ru.gits.core.result;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.session.SessionTask;

/** Rule-based session trust indicators for one task (v1.0, experimental). */
@Entity
@Table(name = "session_indicators")
public class SessionIndicators extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_task_id", nullable = false, unique = true)
    private SessionTask sessionTask;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String indicators;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false)
    private TrustLevel trustLevel;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected SessionIndicators() {
    }

    public SessionIndicators(SessionTask sessionTask, String indicators, TrustLevel trustLevel, Instant computedAt) {
        this.sessionTask = sessionTask;
        recompute(indicators, trustLevel, computedAt);
    }

    public final void recompute(String indicators, TrustLevel trustLevel, Instant computedAt) {
        this.indicators = indicators;
        this.trustLevel = trustLevel;
        this.computedAt = computedAt;
    }

    public SessionTask getSessionTask() {
        return sessionTask;
    }

    public String getIndicators() {
        return indicators;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
