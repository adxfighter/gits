package ru.gits.core.invite;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    boolean existsByInviteIdAndVersion(UUID inviteId, int version);

    List<Consent> findByInviteIdIn(Collection<UUID> inviteIds);
}
