package ru.gits.core.invite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.Company;
import ru.gits.core.common.BaseEntity;
import ru.gits.core.common.Level;

/** One-time assessment invitation. Only the SHA-256 hash of the link token is stored. */
@Entity
@Table(name = "invite")
public class Invite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "candidate_label", nullable = false)
    private String candidateLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false)
    private Level targetLevel;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected Invite() {
    }

    public Invite(Company company, AppUser createdBy, String candidateLabel, Level targetLevel,
                  String tokenHash, Instant expiresAt, Instant createdAt) {
        this.company = company;
        this.createdBy = createdBy;
        this.candidateLabel = candidateLabel;
        this.targetLevel = targetLevel;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.status = InviteStatus.CREATED;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markStarted(Instant now) {
        this.status = InviteStatus.STARTED;
        this.usedAt = now;
    }

    public void markCompleted() {
        this.status = InviteStatus.COMPLETED;
    }

    public void markExpired() {
        this.status = InviteStatus.EXPIRED;
    }

    public void revoke() {
        this.status = InviteStatus.REVOKED;
    }

    public Company getCompany() {
        return company;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public String getCandidateLabel() {
        return candidateLabel;
    }

    public Level getTargetLevel() {
        return targetLevel;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
