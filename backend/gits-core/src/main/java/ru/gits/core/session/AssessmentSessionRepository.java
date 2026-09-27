package ru.gits.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {

    Optional<AssessmentSession> findByInviteId(UUID inviteId);

    /** Invites of the running sessions whose time is over at {@code now}. */
    @Query(value = """
            SELECT s.invite_id FROM assessment_session s
            WHERE s.status = 'IN_PROGRESS'
              AND s.started_at + make_interval(mins => s.time_limit_min) <= :now
            """, nativeQuery = true)
    List<UUID> findInviteIdsOfOverdueSessions(Instant now);
}
