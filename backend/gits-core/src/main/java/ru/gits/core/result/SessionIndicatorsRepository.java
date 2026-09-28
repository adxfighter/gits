package ru.gits.core.result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionIndicatorsRepository extends JpaRepository<SessionIndicators, UUID> {

    Optional<SessionIndicators> findBySessionTaskId(UUID sessionTaskId);

    List<SessionIndicators> findBySessionTaskSessionIdIn(Collection<UUID> sessionIds);
}
