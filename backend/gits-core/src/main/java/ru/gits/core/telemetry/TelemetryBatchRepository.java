package ru.gits.core.telemetry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryBatchRepository extends JpaRepository<TelemetryBatch, UUID> {

    boolean existsBySessionTaskIdAndSeq(UUID sessionTaskId, int seq);

    List<TelemetryBatch> findBySessionTaskIdOrderBySeq(UUID sessionTaskId);

    /** The batch just before {@code seq} (batches may arrive out of order). */
    Optional<TelemetryBatch> findFirstBySessionTaskIdAndSeqLessThanOrderBySeqDesc(UUID sessionTaskId, int seq);

    /** The last batch of the task: where a reloaded page continues seq and the time scale. */
    Optional<TelemetryBatch> findFirstBySessionTaskIdOrderBySeqDesc(UUID sessionTaskId);

    /** The batch just after {@code seq}. */
    Optional<TelemetryBatch> findFirstBySessionTaskIdAndSeqGreaterThanOrderBySeqAsc(UUID sessionTaskId, int seq);

    /** A page of batches from {@code seq} on, for the replay. */
    List<TelemetryBatch> findBySessionTaskIdAndSeqGreaterThanEqualOrderBySeq(UUID sessionTaskId, int seq, Limit limit);
}
