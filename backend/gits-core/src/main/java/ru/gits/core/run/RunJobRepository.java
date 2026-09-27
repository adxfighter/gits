package ru.gits.core.run;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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

    long countBySessionTaskId(UUID sessionTaskId);

    boolean existsBySessionTaskSessionIdAndStatusIn(UUID sessionId, List<RunStatus> statuses);
}
