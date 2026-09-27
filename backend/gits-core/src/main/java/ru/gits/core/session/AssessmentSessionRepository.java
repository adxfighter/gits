package ru.gits.core.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {

    Optional<AssessmentSession> findByInviteId(UUID inviteId);

    List<AssessmentSession> findByStatus(SessionStatus status);
}
