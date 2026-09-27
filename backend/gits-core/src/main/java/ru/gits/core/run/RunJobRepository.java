package ru.gits.core.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RunJobRepository extends JpaRepository<RunJob, UUID> {

    /**
     * Locks up to {@code limit} oldest queued jobs. Must run inside a transaction; concurrent workers
     * skip rows already locked by others, so a job is never claimed twice.
     */
    @Query(value = """
            SELECT * FROM run_job
            WHERE status = 'QUEUED'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RunJob> claimQueued(int limit);

    /** Loads a job with its task variant and all variant files (including hidden tests) in one query. */
    @Query("""
            select j from RunJob j
            join fetch j.sessionTask st
            join fetch st.variant v
            left join fetch v.files
            where j.id = :id
            """)
    Optional<RunJob> findWithTaskFiles(UUID id);

    /** Bulk status change, e.g. RUNNING -> ERROR for jobs interrupted by a runner restart. */
    @Modifying
    @Query("update RunJob j set j.status = :to, j.finishedAt = :now where j.status = :from")
    int changeStatus(RunStatus from, RunStatus to, Instant now);

    long countBySessionTaskId(UUID sessionTaskId);

    boolean existsBySessionTaskSessionIdAndStatusIn(UUID sessionId, List<RunStatus> statuses);

    long countBySessionTaskIdAndMode(UUID sessionTaskId, RunMode mode);

    /** A run of the candidate's own session; other sessions' runs are not found. */
    Optional<RunJob> findByIdAndSessionTaskSessionInviteId(UUID id, UUID inviteId);

    /** The latest run of a task, if any. */
    Optional<RunJob> findFirstBySessionTaskIdOrderByCreatedAtDesc(UUID sessionTaskId);
}
