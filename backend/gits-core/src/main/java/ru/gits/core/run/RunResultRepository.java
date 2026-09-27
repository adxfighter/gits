package ru.gits.core.run;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunResultRepository extends JpaRepository<RunResult, UUID> {

    Optional<RunResult> findByRunJobId(UUID runJobId);
}
