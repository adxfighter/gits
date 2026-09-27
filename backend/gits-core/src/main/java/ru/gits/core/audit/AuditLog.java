package ru.gits.core.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;

@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String details;

    @Column(nullable = false)
    private Instant at;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String entity, UUID entityId, String details, Instant at) {
        this.actor = actor;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.details = details;
        this.at = at;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getAt() {
        return at;
    }
}
