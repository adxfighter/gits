package ru.gits.core.result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.gits.core.task.TaskKind;

public interface SessionIndicatorsRepository extends JpaRepository<SessionIndicators, UUID> {

    Optional<SessionIndicators> findBySessionTaskId(UUID sessionTaskId);

    List<SessionIndicators> findBySessionTaskSessionIdIn(Collection<UUID> sessionIds);

    /** Trust level of a task of one of the sessions, without loading the tasks. */
    record TaskTrust(UUID sessionId, UUID sessionTaskId, TaskKind kind, TrustLevel trustLevel) {
    }

    @Query("""
            select new ru.gits.core.result.SessionIndicatorsRepository$TaskTrust(
                st.session.id, st.id, st.kind, i.trustLevel)
            from SessionIndicators i join i.sessionTask st
            where st.session.id in :sessionIds
            """)
    List<TaskTrust> findTrustOfSessions(Collection<UUID> sessionIds);
}
