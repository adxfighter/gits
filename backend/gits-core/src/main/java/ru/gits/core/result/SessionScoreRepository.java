package ru.gits.core.result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionScoreRepository extends JpaRepository<SessionScore, UUID> {

    Optional<SessionScore> findBySessionId(UUID sessionId);

    List<SessionScore> findBySessionIdIn(Collection<UUID> sessionIds);
}
