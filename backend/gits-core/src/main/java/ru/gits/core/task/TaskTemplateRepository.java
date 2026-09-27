package ru.gits.core.task;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

    Optional<TaskTemplate> findByCode(String code);
}
