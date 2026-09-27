package ru.gits.core.common;

import java.util.UUID;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** UUID-identified entity; identity-based equality once persisted. */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    public UUID getId() {
        return id;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !getClass().equals(other.getClass())) {
            return false;
        }
        return id != null && id.equals(((BaseEntity) other).id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
