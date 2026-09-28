package ru.gits.core.run;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunResultRepository extends JpaRepository<RunResult, UUID> {

    Optional<RunResult> findByRunJobId(UUID runJobId);

    List<RunResult> findByRunJobIdIn(Collection<UUID> runJobIds);
}
