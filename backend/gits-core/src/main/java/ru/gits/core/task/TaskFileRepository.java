package ru.gits.core.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskFileRepository extends JpaRepository<TaskFile, UUID> {

    List<TaskFile> findByVariantIdAndKindIn(UUID variantId, Collection<FileKind> kinds);
}
