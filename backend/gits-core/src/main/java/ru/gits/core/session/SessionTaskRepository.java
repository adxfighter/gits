package ru.gits.core.session;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTaskRepository extends JpaRepository<SessionTask, UUID> {

    List<SessionTask> findBySessionIdOrderByOrderNo(UUID sessionId);
}
