package ru.gits.core.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.common.Level;

@Entity
@Table(name = "task_variant")
public class TaskVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private TaskTemplate template;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    private String domain;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "difficulty_params", nullable = false)
    private String difficultyParams;

    @Column(name = "statement_md", nullable = false)
    private String statementMd;

    @Column(name = "time_limit_min", nullable = false)
    private int timeLimitMin;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariantStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_report")
    private String validationReport;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskFile> files = new ArrayList<>();

    protected TaskVariant() {
    }

    public TaskVariant(TaskTemplate template, String code, TaskKind kind, Level level, String domain,
                       String difficultyParams, String statementMd, int timeLimitMin, String contentHash,
                       String validationReport, Instant now) {
        this.template = template;
        this.code = code;
        this.kind = kind;
        this.level = level;
        this.domain = domain;
        this.difficultyParams = difficultyParams;
        this.statementMd = statementMd;
        this.timeLimitMin = timeLimitMin;
        this.contentHash = contentHash;
        this.validationReport = validationReport;
        this.status = VariantStatus.VALIDATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public TaskFile addFile(FileKind fileKind, String path, String content, boolean editable) {
        var file = new TaskFile(this, fileKind, path, content, editable);
        files.add(file);
        return file;
    }

    public void disable(Instant now) {
        this.status = VariantStatus.DISABLED;
        this.updatedAt = now;
    }

    public TaskTemplate getTemplate() {
        return template;
    }

    public String getCode() {
        return code;
    }

    public TaskKind getKind() {
        return kind;
    }

    public Level getLevel() {
        return level;
    }

    public String getDomain() {
        return domain;
    }

    public String getDifficultyParams() {
        return difficultyParams;
    }

    public String getStatementMd() {
        return statementMd;
    }

    public int getTimeLimitMin() {
        return timeLimitMin;
    }

    public String getContentHash() {
        return contentHash;
    }

    public VariantStatus getStatus() {
        return status;
    }

    public String getValidationReport() {
        return validationReport;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<TaskFile> getFiles() {
        return Collections.unmodifiableList(files);
    }
}
