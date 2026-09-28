package ru.gits.core.session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SessionTaskRepository extends JpaRepository<SessionTask, UUID> {

    List<SessionTask> findBySessionIdOrderByOrderNo(UUID sessionId);

    /** A task of the candidate's own session; other sessions' tasks are not found. */
    Optional<SessionTask> findByIdAndSessionInviteId(UUID id, UUID inviteId);

    /** Variant codes given in the company's {@code sessions} latest sessions (to vary tasks between candidates). */
    @Query(value = """
            SELECT DISTINCT v.code
            FROM session_task st
            JOIN task_variant v ON v.id = st.variant_id
            WHERE st.session_id IN (
                SELECT s.id FROM assessment_session s
                JOIN invite i ON i.id = s.invite_id
                WHERE i.company_id = :companyId
                ORDER BY s.started_at DESC
                LIMIT :sessions)
            """, nativeQuery = true)
    List<String> findVariantCodesOfRecentSessions(UUID companyId, int sessions);

    /** A task of a session of the employer's own company. */
    Optional<SessionTask> findByIdAndSessionInviteCompanyId(UUID id, UUID companyId);

    /** Tasks of several sessions (the research export). */
    List<SessionTask> findBySessionIdInOrderBySessionIdAscOrderNoAsc(Collection<UUID> sessionIds);

    /** How many times each variant was given to candidates (the admin's task bank). */
    @Query("SELECT t.variant.id AS variantId, COUNT(t) AS issued FROM SessionTask t GROUP BY t.variant.id")
    List<VariantIssues> countIssuedByVariant();

    interface VariantIssues {
        UUID getVariantId();

        long getIssued();
    }
}
