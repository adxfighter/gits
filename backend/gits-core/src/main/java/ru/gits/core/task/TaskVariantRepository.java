package ru.gits.core.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.gits.core.common.Level;

public interface TaskVariantRepository extends JpaRepository<TaskVariant, UUID> {

    Optional<TaskVariant> findByCode(String code);

    List<TaskVariant> findByKindAndLevelAndStatus(TaskKind kind, Level level, VariantStatus status);

    List<TaskVariant> findByStatus(VariantStatus status);
}
