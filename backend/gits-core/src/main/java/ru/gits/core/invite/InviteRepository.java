package ru.gits.core.invite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    List<Invite> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** Lightweight status lookup used on every candidate request. */
    @Query("select i.status from Invite i where i.id = :id")
    Optional<InviteStatus> findStatusById(UUID id);
}
