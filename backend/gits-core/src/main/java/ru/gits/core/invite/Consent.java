package ru.gits.core.invite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;

/** Candidate's acceptance of a specific version of the data processing consent text. */
@Entity
@Table(name = "consent")
public class Consent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_id", nullable = false)
    private Invite invite;

    @Column(nullable = false)
    private int version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent")
    private String userAgent;

    protected Consent() {
    }

    public Consent(Invite invite, int version, Instant acceptedAt, String ipHash, String userAgent) {
        this.invite = invite;
        this.version = version;
        this.acceptedAt = acceptedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
    }

    public Invite getInvite() {
        return invite;
    }

    public int getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
