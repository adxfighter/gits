package ru.gits.core.telemetry;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryBatchRepository extends JpaRepository<TelemetryBatch, UUID> {

    boolean existsBySessionTaskIdAndSeq(UUID sessionTaskId, int seq);

    List<TelemetryBatch> findBySessionTaskIdOrderBySeq(UUID sessionTaskId);
}
