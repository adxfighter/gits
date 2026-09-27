package ru.gits.core.session;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.invite.Invite;

@Entity
@Table(name = "assessment_session")
public class AssessmentSession extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_id", nullable = false, unique = true)
    private Invite invite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "time_limit_min", nullable = false)
    private int timeLimitMin;

    @Column(name = "random_seed", nullable = false)
    private long randomSeed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AssessmentSession() {
    }

    public AssessmentSession(Invite invite, int timeLimitMin, long randomSeed, Instant startedAt) {
        this.invite = invite;
        this.timeLimitMin = timeLimitMin;
        this.randomSeed = randomSeed;
        this.startedAt = startedAt;
        this.status = SessionStatus.IN_PROGRESS;
    }

    public Instant deadline() {
        return startedAt.plus(Duration.ofMinutes(timeLimitMin));
    }

    public void finish(Instant now) {
        this.status = SessionStatus.FINISHED;
        this.finishedAt = now;
    }

    public void expire(Instant now) {
        this.status = SessionStatus.EXPIRED;
        this.finishedAt = now;
    }

    public Invite getInvite() {
        return invite;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getTimeLimitMin() {
        return timeLimitMin;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
