package ru.gits.core.task;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import ru.gits.core.common.BaseEntity;
import ru.gits.core.common.Level;

@Entity
@Table(name = "task_template")
public class TaskTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    /** JSON array of competency codes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String competencies;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_level", nullable = false)
    private Level baseLevel;

    /** JSON object describing difficulty parameters and their allowed values. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "difficulty_model", nullable = false)
    private String difficultyModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskTemplate() {
    }

    public TaskTemplate(String code, String title, String competencies, Level baseLevel,
                        String difficultyModel, Instant now) {
        this.code = code;
        this.createdAt = now;
        update(title, competencies, baseLevel, difficultyModel, now);
    }

    public final void update(String title, String competencies, Level baseLevel, String difficultyModel, Instant now) {
        this.title = title;
        this.competencies = competencies;
        this.baseLevel = baseLevel;
        this.difficultyModel = difficultyModel;
        this.updatedAt = now;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getCompetencies() {
        return competencies;
    }

    public Level getBaseLevel() {
        return baseLevel;
    }

    public String getDifficultyModel() {
        return difficultyModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
