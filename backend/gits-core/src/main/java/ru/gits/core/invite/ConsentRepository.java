package ru.gits.core.invite;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    boolean existsByInviteIdAndVersion(UUID inviteId, int version);
}
