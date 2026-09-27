package ru.gits.core.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Case-insensitive lookup matching the unique index on lower(email). */
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmail(String email);
}
