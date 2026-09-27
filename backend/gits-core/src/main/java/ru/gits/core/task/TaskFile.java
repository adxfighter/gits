package ru.gits.core.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;

@Entity
@Table(name = "task_file")
public class TaskFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private TaskVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileKind kind;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean editable;

    protected TaskFile() {
    }

    TaskFile(TaskVariant variant, FileKind kind, String path, String content, boolean editable) {
        this.variant = variant;
        this.kind = kind;
        this.path = path;
        this.content = content;
        this.editable = editable;
    }

    public TaskVariant getVariant() {
        return variant;
    }

    public FileKind getKind() {
        return kind;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public boolean isEditable() {
        return editable;
    }
}
