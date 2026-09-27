package ru.gits.core.common;

import java.util.UUID;

import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * UUID-identified entity with identity-based equality once persisted. Equality compares the persistent
 * class, not the runtime class, so an entity equals its own Hibernate lazy proxy.
 */
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
        if (!(other instanceof BaseEntity that) || persistentClass(this) != persistentClass(other)) {
            return false;
        }
        // getId() on a proxy returns the identifier without initializing it
        UUID thisId = getId();
        return thisId != null && thisId.equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return persistentClass(this).hashCode();
    }

    private static Class<?> persistentClass(Object entity) {
        return entity instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
    }
}
