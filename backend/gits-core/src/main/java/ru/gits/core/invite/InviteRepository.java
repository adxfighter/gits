package ru.gits.core.invite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    List<Invite> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** Lightweight status lookup used on every candidate request. */
    @Query("select i.status from Invite i where i.id = :id")
    Optional<InviteStatus> findStatusById(UUID id);

    /**
     * The invite locked until the end of the transaction. Every change of a candidate's session takes this lock
     * first, so requests of one candidate and the expiry scheduler never interleave.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.id = :id")
    Optional<Invite> lockById(UUID id);

    /** An invite of the employer's own company; other companies' invites are not found. */
    Optional<Invite> findByIdAndCompanyId(UUID id, UUID companyId);

    /** The invite of a link, locked like {@link #lockById}: entering and revoking never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :tokenHash")
    Optional<Invite> lockByTokenHash(String tokenHash);
}
