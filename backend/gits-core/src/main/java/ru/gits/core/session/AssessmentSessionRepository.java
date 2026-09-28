package ru.gits.core.session;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
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

    /** A session of the employer's own company. */
    Optional<AssessmentSession> findByIdAndInviteCompanyId(UUID id, UUID companyId);

    List<AssessmentSession> findByInviteIdIn(Collection<UUID> inviteIds);

    /** Every session, newest first (the admin's list), with its invite and company in the same query. */
    @EntityGraph(attributePaths = {"invite", "invite.company"})
    List<AssessmentSession> findAllByOrderByStartedAtDesc();

    /** Sessions started in {@code [from, to)} (the research export), with their invite and company. */
    @EntityGraph(attributePaths = {"invite", "invite.company"})
    List<AssessmentSession> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(Instant from,
                                                                                            Instant to);

    /**
     * Finished sessions without a score whose submits are all checked (or taken as lost: waiting since before
     * {@code stuckBefore}), oldest first.
     */
    @Query(value = """
            SELECT s.id FROM assessment_session s
            WHERE s.status IN ('FINISHED', 'EXPIRED')
              AND NOT EXISTS (SELECT 1 FROM session_score sc WHERE sc.session_id = s.id)
              AND NOT EXISTS (SELECT 1 FROM run_job j JOIN session_task st ON st.id = j.session_task_id
                              WHERE st.session_id = s.id AND j.mode = 'SUBMIT'
                                AND j.status IN ('QUEUED', 'RUNNING') AND j.created_at > :stuckBefore)
            ORDER BY s.finished_at
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findIdsReadyForScoring(int limit, Instant stuckBefore);
}
